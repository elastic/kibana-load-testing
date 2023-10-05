package org.kibanaLoadTest.helpers

import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, IntJsonFormat}
import spray.json.lenses.JsonLenses._
import io.circe.Json
import io.circe.parser.parse
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpDelete, HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.helpers.Helper.checkFilesExist
import org.slf4j.{Logger, LoggerFactory}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Using}
import scala.concurrent.duration.DurationInt

case class SavedObject(id: String, soType: String)

class KbnClient(
    baseUrl: String,
    username: String,
    password: String,
    providerName: String,
    providerType: String
) {
  private val logger: Logger = LoggerFactory.getLogger("KbnClient")
  private val MAX_CONCURRENT_CONNECTIONS = 20
  private val loginPayload =
    s"""{"providerType":"$providerType","providerName":"$providerName","currentURL":"$baseUrl/login","params":{"username":"$username","password":"$password"}}"""
  private var version: String = null
  private var authCookie: String = null
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
      getAuthCookie(client)
    }

    (client, connManager)
  }

  private def getAuthCookie(client: CloseableHttpClient): String = {
    if (this.authCookie == null) {
      this.authCookie = doLogin(client)
    }
    this.authCookie
  }

  def getVersion(): String = {
    if (this.version == null) {
      val (client, connManager) =
        getClientAndConnectionManager(withAuth = false)
      val getIndexHtmlRequest = new HttpGet(s"$baseUrl/login")
      Using.resources(client, connManager) { (client, connManager) =>
        {
          Using(client.execute(getIndexHtmlRequest)) { response =>
            EntityUtils.toString(response.getEntity)
          } match {
            case Success(responseStr) =>
              val regExp = "\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?".r
              regExp.findFirstIn(responseStr) match {
                case Some(version) => this.version = version
                case None =>
                  throw new RuntimeException(
                    "Cannot parse kbn-version in login html page"
                  )
              }
            case Failure(error) => throw new RuntimeException(error)
          }
        }
      }
    }
    // cached for client instance
    this.version
  }

  private def doLogin(client: CloseableHttpClient): String = {
    val loginRequest = new HttpPost(s"$baseUrl/internal/security/login")
    loginRequest.addHeader("Content-Type", "application/json")
    // required for serverless API call
    loginRequest.addHeader("x-elastic-internal-origin", "Kibana")
    loginRequest.addHeader("kbn-version", this.getVersion())
    loginRequest.setEntity(new StringEntity(loginPayload))
    Using(client.execute(loginRequest)) { response =>
      response
    } match {
      case Success(response) =>
        if (response.getStatusLine.getStatusCode == 200) {
          Option(response.getFirstHeader("set-cookie")) match {
            case Some(value) => value.getValue.split(";")(0)
            case _ =>
              throw new RuntimeException("Response has no 'set-cookie' header")
          }
        } else {
          throw new RuntimeException(
            s"Failed to login: ${response.getStatusLine}"
          )
        }
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
      val importRequest =
        new HttpPost(s"$baseUrl/api/saved_objects/_import?overwrite=true")
      importRequest.addHeader("Connection", "keep-alive")
      importRequest.addHeader("kbn-version", this.getVersion())
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
                s"$baseUrl/api/saved_objects/${so.soType}/${so.id}?force=true"
              val deleteRequest = new HttpDelete(url)
              deleteRequest.addHeader("Connection", "keep-alive")
              deleteRequest.addHeader("kbn-version", this.getVersion())
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
              logger.warn(
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
    if (count < 1) {
      throw new IllegalArgumentException("'count' must be above 0")
    }
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
          (1 to count)
            .map(i =>
              Future {
                doLogin(client)
              }
            )
            .toList
        )

        requestsFuture.onComplete {
          case Success(res) =>
            logger.debug(
              s"Successfully generated cookies: ${res.length} out of $count"
            )
          case Failure(e) =>
            throw new RuntimeException("Failed to generate cookies", e)
        }
        Await.result(requestsFuture, 120.seconds) // 2 min timeout
      }
    }
  }

  def getKibanaStatusInfo(): String = {
    val (client, connManager) = getClientAndConnectionManager()
    val statusRequest = new HttpGet(
      baseUrl + "/api/status"
    )
    Using(client.execute(statusRequest)) { response =>
      EntityUtils.toString(response.getEntity)
    } match {
      case Success(responseBody) => responseBody
      case Failure(error) =>
        throw new RuntimeException(s"Failed to call Kibana status api: $error")
    }
  }

  def addSampleData(dataType: String): Unit = {
    logger.info(s"Loading sample data: $dataType")
    val (client, connManager) = getClientAndConnectionManager()
    Using.resources(client, connManager) { (client, connManager) =>
      {
        val loadDataRequest =
          new HttpPost(s"$baseUrl/api/sample_data/$dataType")
        loadDataRequest.addHeader("Connection", "keep-alive")
        loadDataRequest.addHeader("kbn-version", this.getVersion())
        Using.resources(client, connManager) { (client, connManager) =>
          {
            Using(client.execute(loadDataRequest)) { response =>
              response
            } match {
              case Success(response) => {
                if (response.getStatusLine.getStatusCode != 200) {
                  throw new RuntimeException(
                    s"Adding sample data failed: ${response.getStatusLine}"
                  )
                }
              }
              case Failure(error) =>
                logger.error("Exception occurred during loading sample data")
                throw new RuntimeException(error)
            }
          }
        }
      }
    }
  }

  def removeSampleData(dataType: String): Unit = {
    logger.info(s"Removing sample data: $dataType")
    val (client, connManager) = getClientAndConnectionManager()
    Using.resources(client, connManager) { (client, connManager) =>
      {
        val deleteDataRequest =
          new HttpDelete(s"$baseUrl/api/sample_data/$dataType")
        deleteDataRequest.addHeader("Connection", "keep-alive")
        deleteDataRequest.addHeader("kbn-version", this.getVersion())
        Using.resources(client, connManager) { (client, connManager) =>
          {
            Using(client.execute(deleteDataRequest)) { response =>
              response
            } match {
              case Success(response) => {
                if (response.getStatusLine.getStatusCode != 204) {
                  throw new RuntimeException(
                    s"Removing sample data failed: ${response.getStatusLine}"
                  )
                }
              }
              case Failure(error) =>
                logger.error("Exception occurred during sample data removal")
                throw new RuntimeException(error)
            }
          }
        }
      }
    }
  }
}
