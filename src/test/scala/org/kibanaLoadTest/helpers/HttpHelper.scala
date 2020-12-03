package org.kibanaLoadTest.helpers

import org.apache.http.client.methods.{HttpDelete, HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.KibanaConfiguration
import org.slf4j.{Logger, LoggerFactory}

class HttpHelper(appConfig: KibanaConfiguration) {

  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  val logger: Logger = LoggerFactory.getLogger("HttpHelper")

  def loginIfNeeded(httpClient: CloseableHttpClient): HttpHelper = {
    if (appConfig.isSecurityEnabled) {
      val loginRequest = new HttpPost(
        appConfig.baseUrl + "/internal/security/login"
      )
      loginHeaders foreach {
        case (key, value) => loginRequest.addHeader(key, value)
      }
      loginRequest.setEntity(new StringEntity(appConfig.loginPayload))
      val loginResponse = httpClient.execute(loginRequest)

      if (
        loginResponse.getStatusLine.getStatusCode != appConfig.loginStatusCode
      ) {
        throw new RuntimeException(
          s"Login to Kibana failed: ${EntityUtils.toString(loginResponse.getEntity, "UTF-8")}"
        )
      }
    }
    this
  }

  def removeSampleData(data: String): Unit = {
    var statusCode = 500
    var responseBody = ""
    val httpClient = HttpClientBuilder.create.build
    try {
      this.loginIfNeeded(httpClient)
      val sampleDataRequest = new HttpDelete(
        appConfig.baseUrl + s"/api/sample_data/${data}"
      )
      sampleDataRequest.addHeader("Connection", "keep-alive")
      sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

      val sampleDataResponse = httpClient.execute(sampleDataRequest)
      statusCode = sampleDataResponse.getStatusLine.getStatusCode
      responseBody = EntityUtils.toString(sampleDataResponse.getEntity, "UTF-8")
    } catch {
      case _: Throwable => {
        logger.error("Exception occurred during unloading sample data")
      }
    } finally {
      httpClient.close()
    }

    if (statusCode != 204) {
      throw new RuntimeException(
        s"Deleting sample data failed: ${responseBody}"
      )
    }
  }

  def addSampleData(data: String): Unit = {
    var statusCode = 500
    var responseBody = ""
    val httpClient = HttpClientBuilder.create.build
    try {
      this.loginIfNeeded(httpClient)
      val sampleDataRequest = new HttpPost(
        appConfig.baseUrl + s"/api/sample_data/${data}"
      )
      sampleDataRequest.addHeader("Connection", "keep-alive")
      sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

      val sampleDataResponse = httpClient.execute(sampleDataRequest)
      statusCode = sampleDataResponse.getStatusLine.getStatusCode
      responseBody = EntityUtils.toString(sampleDataResponse.getEntity, "UTF-8")
    } catch {
      case _: Throwable => {
        logger.error("Exception occurred during loading sample data")
      }
    } finally {
      httpClient.close()
    }

    if (statusCode != 200) {
      throw new RuntimeException(
        s"Adding sample data failed: ${responseBody}"
      )
    }
  }

  def getStatus(): String = {
    var responseBody = ""
    val httpClient = HttpClientBuilder.create.build
    try {
      this.loginIfNeeded(httpClient)
      val statusRequest = new HttpGet(
        appConfig.baseUrl + "/api/status"
      )
      val statusResponse = httpClient.execute(statusRequest)
      responseBody = EntityUtils.toString(statusResponse.getEntity, "UTF-8")
    } catch {
      case _: Throwable => {
        logger.error("Exception occurred during getting Kibana status")
      }
    } finally {
      httpClient.close()
    }

    responseBody
  }
}
