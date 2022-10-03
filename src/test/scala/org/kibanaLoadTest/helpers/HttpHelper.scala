package org.kibanaLoadTest.helpers

import io.gatling.core.Predef._
import io.gatling.core.filter.DenyList
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import org.apache.http.client.methods.{HttpDelete, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.KibanaConfiguration
import org.slf4j.{Logger, LoggerFactory}

class HttpHelper(config: KibanaConfiguration) {
  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )
  val logger: Logger = LoggerFactory.getLogger("HttpHelper")

  def loginIfNeeded(httpClient: CloseableHttpClient): HttpHelper = {
    if (config.isSecurityEnabled) {
      var statusCode = 0
      var response = ""
      val url = config.baseUrl + "/internal/security/login"
      val loginRequest = new HttpPost(url)
      loginHeaders foreach {
        case (key, value) => loginRequest.addHeader(key, value)
      }
      try {
        loginRequest.setEntity(new StringEntity(config.loginPayload))
        val loginResponse = httpClient.execute(loginRequest)
        statusCode = loginResponse.getStatusLine.getStatusCode
        response = EntityUtils.toString(loginResponse.getEntity, "UTF-8")
      } catch {
        case e: Throwable =>
          logger.error(
            s"Login to Kibana failed:\nPOST $url\nHeaders: ${loginHeaders
              .toString()}\nBody: ${config.loginPayload}\n${e.getStackTrace}"
          )
          throw e
      }

      if (statusCode != config.loginStatusCode) {
        throw new RuntimeException(
          s"Login to Kibana failed with code ${statusCode}: $response"
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
        config.baseUrl + s"/api/sample_data/$data"
      )
      sampleDataRequest.addHeader("Connection", "keep-alive")
      sampleDataRequest.addHeader("kbn-version", config.buildVersion)

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
        config.baseUrl + ("/api/sample_data/" + data)
      )
      sampleDataRequest.addHeader("Connection", "keep-alive")
      sampleDataRequest.addHeader("kbn-version", config.buildVersion)

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

  def getDefaultHeaders: Map[String, String] = {
    Map(
      "Connection" -> "keep-alive",
      "kbn-version" -> config.buildVersion,
      "Content-Type" -> "application/json",
      "Accept" -> "*/*",
      "Origin" -> config.baseUrl,
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
      .baseUrl(config.baseUrl)
      .inferHtmlResources(
        allow = AllowList(),
        deny = new DenyList(
          Seq(
            """.*\.js""",
            """.*\.svg""",
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
      )
      .acceptHeader(
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
      )
      .acceptEncodingHeader("br, gzip, deflate")
      .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
      .userAgentHeader(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"
      )
  }
}
