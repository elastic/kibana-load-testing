package org.kibanaLoadTest.helpers

import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, IntJsonFormat}
import spray.json.lenses.JsonLenses._
import io.circe.Json
import io.circe.parser.parse
import org.apache.http.client.methods.{
  CloseableHttpResponse,
  HttpDelete,
  HttpPost
}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.checkFilesExist
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration.DurationInt

case class SavedObject(id: String, soType: String)

class KbnClient(config: KibanaConfiguration) {
  private val logger: Logger = LoggerFactory.getLogger("KbnClient")
  private def getJsonPath(path: Path) =
    if (path.toString.endsWith(".json")) path else Paths.get(path.toString + ".json")

  def getCookie(): String = {
    val loginHeaders = Map(
      "Content-Type" -> "application/json",
      "kbn-xsrf" -> "xsrf"
    )
    val url = config.baseUrl + "/internal/security/login"
    val loginRequest = new HttpPost(url)
    loginHeaders foreach {
      case (key, value) => loginRequest.addHeader(key, value)
    }
    val client = HttpClientBuilder.create.build
    loginRequest.setEntity(new StringEntity(config.loginPayload))
    val loginResponse: CloseableHttpResponse = null
    try {
      val loginResponse = client.execute(loginRequest)
      loginResponse
        .getHeaders("set-cookie")(0)
        .getValue
        .split(";")(0)
    } catch {
      case e: Throwable =>
        throw new RuntimeException("Login exception", e)
    } finally {
      if (loginResponse != null) loginResponse.close()
      client.close()
    }
  }

  def load(path: Path): Unit = {
    val archivePath = this.getJsonPath(path)
    checkFilesExist(archivePath)
    val savedObjectStringList = Helper.readArchiveFile(archivePath)
    logger.info(
      s"Importing ${savedObjectStringList.length} saved objects from [${archivePath.toString}]"
    )
    val cookie = this.getCookie()
    val importRequest = new HttpPost(
      config.baseUrl + "/api/saved_objects/_import?overwrite=true"
    )
    importRequest.addHeader("Connection", "keep-alive")
    importRequest.addHeader("kbn-version", config.buildVersion)
    importRequest.addHeader("Cookie", cookie)

    val builder = MultipartEntityBuilder.create
    builder.addBinaryBody(
      "file",
      new ByteArrayInputStream(
        savedObjectStringList.mkString("\n").getBytes(StandardCharsets.UTF_8)
      ),
      ContentType.APPLICATION_OCTET_STREAM,
      "import.ndjson"
    )
    val multipart = builder.build()
    importRequest.setEntity(multipart)
    var response: CloseableHttpResponse = null
    val client = HttpClientBuilder.create.build
    try {
      response = client.execute(importRequest)
      val responseBody = EntityUtils.toString(response.getEntity)

      val isSuccess = responseBody
        .extract[Boolean](Symbol("success"))
      val successCount = responseBody
        .extract[Int](Symbol("successCount"))
      if (isSuccess && (successCount == savedObjectStringList.length)) {
        logger.info("Import finished successfully")
      } else {
        logger.error(
          s"Import finished with errors, $successCount out of ${savedObjectStringList.length} saved objects imported"
        )
      }
    } catch {
      case e: Throwable =>
        throw new RuntimeException("Exception during saved objects import", e)
    } finally {
      if (response != null) response.close()
      client.close()
    }
  }

  def unload(path: Path): Unit = {
    val archivePath = this.getJsonPath(path)
    checkFilesExist(archivePath)
    val savedObjects = Helper
      .readArchiveFile(archivePath)
      .map(str => {
        val json = parse(str).getOrElse(Json.Null)
        val id = json.hcursor.get[String]("id").getOrElse(null)
        val soType = json.hcursor.get[String]("type").getOrElse(null)
        SavedObject(id, soType)
      })

    logger.info(
      s"Deleting ${savedObjects.length} saved objects from [${archivePath.toString}]"
    )
    // login to Kibana once and reuse cookie for concurrent requests
    val cookie = this.getCookie()
    // Allow 20 requests to be executing at once
    implicit val executionContext = ExecutionContext.fromExecutor(
      new java.util.concurrent.ForkJoinPool(
        20
      )
    )

    val requestsFuture = Future.sequence(
      savedObjects.map(so =>
        Future {
          val client = HttpClientBuilder.create.build
          try {
            val url =
              config.baseUrl + s"/api/saved_objects/${so.soType}/${so.id}?force=true"
            val deleteRequest = new HttpDelete(url)
            deleteRequest.addHeader("Connection", "keep-alive")
            deleteRequest.addHeader("kbn-version", config.buildVersion)
            deleteRequest.addHeader("Cookie", cookie)
            val response = client.execute(deleteRequest)
            response.getStatusLine
          } finally client.close()
        }
      )
    )

    requestsFuture.onComplete {
      case Success(res) =>
        val successCount = res.count(r => r.getStatusCode == 200)
        if (successCount == res.length) {
          logger.info("All saved objects deleted")
        } else {
          logger.info(
            s"${res.length - successCount} out of ${res.length} saved objects were not deleted"
          )
        }
      case Failure(e) =>
        throw new RuntimeException("Failed to delete saved objects", e)
    }
    Await.ready(requestsFuture, 120.seconds) // 2 min timeout
  }
}
