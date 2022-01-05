package org.kibanaLoadTest.helpers

import io.circe.Json
import io.circe.parser.parse
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.{
  CloseableHttpResponse,
  HttpDelete,
  HttpGet,
  HttpPost
}
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.{
  BasicCredentialsProvider,
  CloseableHttpClient,
  HttpClientBuilder
}
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.KibanaConfiguration
import org.slf4j.{Logger, LoggerFactory}

import java.io.File

class HttpHelper(appConfig: KibanaConfiguration) {
  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )
  val logger: Logger = LoggerFactory.getLogger("HttpHelper")

  def loginIfNeeded(httpClient: CloseableHttpClient): HttpHelper = {
    if (appConfig.isSecurityEnabled) {
      var statusCode = 0
      var response = ""
      val url = appConfig.baseUrl + "/internal/security/login"
      val loginRequest = new HttpPost(url)
      loginHeaders foreach {
        case (key, value) => loginRequest.addHeader(key, value)
      }
      try {
        loginRequest.setEntity(new StringEntity(appConfig.loginPayload))
        val loginResponse = httpClient.execute(loginRequest)
        statusCode = loginResponse.getStatusLine.getStatusCode
        response = EntityUtils.toString(loginResponse.getEntity, "UTF-8")
      } catch {
        case e: Throwable =>
          logger.error(
            s"Login to Kibana failed:\nPOST $url\nHeaders: ${loginHeaders
              .toString()}\nBody: ${appConfig.loginPayload}\n${e.getStackTrace}"
          )
          throw e
      }

      if (statusCode != appConfig.loginStatusCode) {
        throw new RuntimeException(
          s"Login to Kibana failed with code $statusCode: $response"
        )
      }
    }
    this
  }

  def removeSampleData(data: String): Unit = {
    logger.info("Removing sample data")
    var statusCode = 500
    var responseBody = ""
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    try {
      val sampleDataRequest = new HttpDelete(
        appConfig.baseUrl + s"/api/sample_data/$data"
      )
      sampleDataRequest.addHeader("Connection", "keep-alive")
      sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

      val sampleDataResponse = httpClient.execute(sampleDataRequest)
      statusCode = sampleDataResponse.getStatusLine.getStatusCode
      responseBody = EntityUtils.toString(sampleDataResponse.getEntity, "UTF-8")
    } catch {
      case _: Throwable =>
        logger.error("Exception occurred during unloading sample data")
    } finally {
      httpClient.close()
    }

    if (statusCode != 204)
      throw new RuntimeException {
        s"Deleting sample data failed with code $statusCode: $responseBody"
      }
  }

  def addSampleData(data: String): Unit = {
    logger.info("Loading sample data")
    var statusCode = 500
    var responseBody = ""
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    try {
      val sampleDataRequest = new HttpPost(
        appConfig.baseUrl + ("/api/sample_data/" + data)
      )
      sampleDataRequest.addHeader("Connection", "keep-alive")
      sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

      val sampleDataResponse = httpClient.execute(sampleDataRequest)
      statusCode = sampleDataResponse.getStatusLine.getStatusCode
      responseBody = EntityUtils.toString(sampleDataResponse.getEntity, "UTF-8")
    } catch {
      case _: Throwable =>
        logger.error("Exception occurred during loading sample data")
    } finally {
      httpClient.close()
    }

    if (statusCode != 200) {
      throw new RuntimeException(
        "Adding sample data failed with code " + statusCode + ": " + responseBody
      )
    }
  }

  def importSavedObjects(filePath: String): String = {
    logger.info(s"Importing saved objects from '$filePath'")
    val file = new File(filePath)
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    val importRequest = new HttpPost(
      appConfig.baseUrl + "/api/saved_objects/_import?createNewCopies=true"
    )
    importRequest.addHeader("Connection", "keep-alive")
    importRequest.addHeader("kbn-version", appConfig.buildVersion)
    val builder = MultipartEntityBuilder.create
    builder.addBinaryBody("file", file)
    val multipart = builder.build()
    importRequest.setEntity(multipart)
    var responseBody = ""
    var response: CloseableHttpResponse = null
    try {
      response = httpClient.execute(importRequest)
      responseBody = EntityUtils.toString(response.getEntity)
    } catch {
      case _: Throwable =>
        logger.error("Exception occurred during saved objects import")
    } finally {
      if (response != null) response.close()
      httpClient.close()
    }

    responseBody
  }

  def getStatus: String = {
    logger.info("Kibana status call")
    var responseBody = ""
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    try {
      val statusRequest = new HttpGet(
        appConfig.baseUrl + "/api/status"
      )
      val statusResponse = httpClient.execute(statusRequest)
      responseBody = EntityUtils.toString(statusResponse.getEntity, "UTF-8")
    } catch {
      case _: Throwable =>
        logger.error("Exception occurred during getting Kibana status")
    } finally {
      httpClient.close()
    }

    responseBody
  }

  def getElasticSearchData: Json = {
    logger.info("ES status call")
    var jsonString = ""
    var httpClient: CloseableHttpClient = null
    var response: CloseableHttpResponse = null
    val request = new HttpGet(appConfig.esUrl)
    val provider = new BasicCredentialsProvider
    provider.setCredentials(
      AuthScope.ANY,
      new UsernamePasswordCredentials(appConfig.username, appConfig.password)
    )

    try {
      httpClient =
        HttpClientBuilder.create.setDefaultCredentialsProvider(provider).build
      response = httpClient.execute(request)
      jsonString = EntityUtils.toString(response.getEntity)
    } finally {
      if (response != null) response.close()
      if (httpClient != null) httpClient.close()
    }

    parse(jsonString).getOrElse(Json.Null)
  }

  def getDefaultHeaders: Map[String, String] = {
    Map(
      "Connection" -> "keep-alive",
      "kbn-version" -> appConfig.buildVersion,
      "Content-Type" -> "application/json",
      "Accept" -> "*/*",
      "Origin" -> appConfig.baseUrl,
      "Sec-Fetch-Site" -> "same-origin",
      "Sec-Fetch-Mode" -> "cors",
      "Sec-Fetch-Dest" -> "empty"
    )
  }

  def defaultTextHeaders: Map[String, String] = {
    Map("Content-Type" -> "text/html; charset=utf-8")
  }

  def getProtocol: HttpProtocolBuilder = {
    http
      .baseUrl(appConfig.baseUrl)
      .inferHtmlResources(
        DenyList(
          """.*\.js""",
          """.*\.css""",
          """.*\.gif""",
          """.*\.jpeg""",
          """.*\.jpg""",
          """.*\.ico""",
          """.*\.woff""",
          """.*\.woff2""",
          """.*\.(t|o)tf""",
          """.*\.png""",
          """.*detectportal\.firefox\.com.*"""
        )
      )
      .acceptHeader(
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
      )
      .acceptEncodingHeader("gzip, deflate")
      .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
      .userAgentHeader(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"
      )
  }
}
