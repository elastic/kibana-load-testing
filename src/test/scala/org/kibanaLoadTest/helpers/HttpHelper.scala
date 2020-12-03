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
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    val sampleDataRequest = new HttpDelete(
      appConfig.baseUrl + s"/api/sample_data/${data}"
    )
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)
    httpClient.close()

    if (sampleDataResponse.getStatusLine.getStatusCode != 204) {
      throw new RuntimeException(
        s"Deleting sample data failed: ${EntityUtils
          .toString(sampleDataResponse.getEntity, "UTF-8")}"
      )
    }
  }

  def addSampleData(data: String): HttpHelper = {
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    val sampleDataRequest = new HttpPost(
      appConfig.baseUrl + s"/api/sample_data/${data}"
    )
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)
    httpClient.close()

    if (sampleDataResponse.getStatusLine.getStatusCode != 200) {
      throw new RuntimeException(
        s"Adding sample data failed: ${EntityUtils.toString(sampleDataResponse.getEntity, "UTF-8")}"
      )
    }
    this
  }

  def getStatus(): String = {
    val httpClient = HttpClientBuilder.create.build
    this.loginIfNeeded(httpClient)
    val statusRequest = new HttpGet(
      appConfig.baseUrl + "/api/status"
    )
    val response = httpClient.execute(statusRequest)
    httpClient.close()
    EntityUtils.toString(response.getEntity)
  }
}
