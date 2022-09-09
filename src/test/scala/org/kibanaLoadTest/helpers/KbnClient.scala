package org.kibanaLoadTest.helpers

import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, IntJsonFormat}
import spray.json.lenses.JsonLenses._
import io.circe.Json
import io.circe.parser.parse
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{
  CloseableHttpResponse,
  HttpDelete,
  HttpPost
}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.checkFilesExist
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Using}
import scala.concurrent.duration.DurationInt

case class SavedObject(id: String, soType: String)

class KbnClient(config: KibanaConfiguration) {
  private val logger: Logger = LoggerFactory.getLogger("KbnClient")
  private val MAX_CONCURRENT_CONNECTIONS = 20
  private def getJsonPath(path: Path) =
    if (path.toString.endsWith(".json")) path
    else Paths.get(path.toString + ".json")

  def getClientAndConnectionManager(
      perRouteConnections: Int = MAX_CONCURRENT_CONNECTIONS,
      withAuth: Boolean = true
  ): (CloseableHttpClient, PoolingHttpClientConnectionManager) = {
    val connManager = new PoolingHttpClientConnectionManager()
    // default value is 2
    connManager.setDefaultMaxPerRoute(perRouteConnections)
    // default value is 100, updating for consistency
    connManager.setMaxTotal(perRouteConnections)
    val client = HttpClients
      .custom()
      .setConnectionManager(connManager)
      .build()

    if (withAuth) {
      // login to Kibana
      val loginHeaders = Map(
        "Content-Type" -> "application/json",
        "kbn-xsrf" -> "xsrf"
      )
      val url = config.baseUrl + "/internal/security/login"
      val loginRequest = new HttpPost(url)
      loginHeaders foreach {
        case (key, value) => loginRequest.addHeader(key, value)
      }
      loginRequest.setEntity(new StringEntity(config.loginPayload))
      client.execute(loginRequest)
    }

    (client, connManager)
  }

  def load(path: Path): Unit = {
    val archivePath = this.getJsonPath(path)
    checkFilesExist(archivePath)
    val (client, connManager) = getClientAndConnectionManager()
    Using.resources(client, connManager) { (client, connManager) =>
      val savedObjectStringList = Helper.readArchiveFile(archivePath)
      logger.info(
        s"Importing ${savedObjectStringList.length} saved objects from [${archivePath.toString}]"
      )
      val importRequest = new HttpPost(
        config.baseUrl + "/api/saved_objects/_import?overwrite=true"
      )
      importRequest.addHeader("Connection", "keep-alive")
      importRequest.addHeader("kbn-version", config.buildVersion)
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
      }
    }
  }

  private def doUnload(
      path: Path,
      client: CloseableHttpClient,
      connManager: PoolingHttpClientConnectionManager
  ): Unit = {}

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
    val (client, connManager) = getClientAndConnectionManager()
    Using.resources(client, connManager) { (client, connManager) =>
      {
        // Allow specific amount of requests to be executing at once
        implicit val executionContext = ExecutionContext.fromExecutor(
          new java.util.concurrent.ForkJoinPool(
            MAX_CONCURRENT_CONNECTIONS
          )
        )

        val requestsFuture = Future.sequence(
          savedObjects.map(so =>
            Future {
              var response: CloseableHttpResponse = null
              val url =
                s"${config.baseUrl}/api/saved_objects/${so.soType}/${so.id}?force=true"
              try {
                val deleteRequest = new HttpDelete(url)
                deleteRequest.addHeader("Connection", "keep-alive")
                deleteRequest.addHeader("kbn-version", config.buildVersion)
                response = client.execute(deleteRequest)
                response.getStatusLine
              } catch {
                case e: Throwable =>
                  throw new RuntimeException(
                    s"[$url] SavedObject deletion failed",
                    e
                  )
              } finally {
                if (response != null) response.close()
              }
            }
          )
        )

        requestsFuture.onComplete {
          case Success(res) =>
            val successCount =
              res.count(r => r.getStatusCode == HttpStatus.SC_OK)
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
  }
}
