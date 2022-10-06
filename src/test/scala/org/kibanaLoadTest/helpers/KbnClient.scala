package org.kibanaLoadTest.helpers

import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, IntJsonFormat}
import spray.json.lenses.JsonLenses._
import io.circe.Json
import io.circe.parser.parse
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpDelete, HttpPost}
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
      getCookie(client)
    }

    (client, connManager)
  }

  private def getCookie(client: CloseableHttpClient): String = {
    val loginRequest = new HttpPost(config.baseUrl + "/internal/security/login")
    loginRequest.addHeader("Content-Type", "application/json")
    loginRequest.addHeader("kbn-xsrf", "xsrf")
    loginRequest.setEntity(new StringEntity(config.loginPayload))
    Using(client.execute(loginRequest)) { response =>
      response
    } match {
      case Success(response) =>
        if (response.getStatusLine.getStatusCode == 200) {
          response.getHeaders("set-cookie")(0).getValue.split(";")(0)
        } else
          throw new RuntimeException(
            s"Failed to login: ${response.getStatusLine}"
          )
      case Failure(error) =>
        throw new RuntimeException(s"Login request failed: $error")
    }
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
      Using(client.execute(importRequest)) { response =>
        EntityUtils.toString(response.getEntity)
      } match {
        case Success(responseBody) =>
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
        case Failure(error) =>
          throw new RuntimeException(
            "Exception during saved objects import",
            error
          )
      }
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
              val url =
                s"${config.baseUrl}/api/saved_objects/${so.soType}/${so.id}?force=true"
              val deleteRequest = new HttpDelete(url)
              deleteRequest.addHeader("Connection", "keep-alive")
              deleteRequest.addHeader("kbn-version", config.buildVersion)
              Using(client.execute(deleteRequest)) { response =>
                response.getStatusLine
              } match {
                case Success(status) => status
                case Failure(error) =>
                  throw new RuntimeException(
                    s"[$url] SavedObject deletion failed",
                    error
                  )
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

  def generateCookies(count: Int): List[String] = {
    val (client, connManager) = getClientAndConnectionManager(withAuth = false)
    Using.resources(client, connManager) { (client, connManager) =>
      {
        // Allow specific amount of requests to be executing at once
        implicit val executionContext = ExecutionContext.fromExecutor(
          new java.util.concurrent.ForkJoinPool(
            MAX_CONCURRENT_CONNECTIONS
          )
        )

        val requestsFuture = Future.sequence(
          List
            .empty[Future[String]]
            .padTo(
              count,
              Future {
                getCookie(client)
              }
            )
        )

        requestsFuture.onComplete {
          case Success(res) =>
            logger.info(
              s"Successfully generated cookies: ${res.length} out of $count"
            )
          case Failure(e) =>
            throw new RuntimeException("Failed to generate cookies", e)
        }
        Await.result(requestsFuture, 120.seconds) // 2 min timeout
      }
    }
  }
}
