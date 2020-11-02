package org.kibanaLoadTest.helpers

import org.apache.http.client.methods.{HttpDelete, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.kibanaLoadTest.KibanaConfiguration

class HttpHelper(appConfig: KibanaConfiguration) {

  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )
  private val httpClient = HttpClientBuilder.create.build

  def loginIfNeeded(): HttpHelper = {
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
        throw new RuntimeException("Login to Kibana failed")
      }
    }
    this
  }

  def removeSampleData(data: String): HttpHelper = {
    val sampleDataRequest = new HttpDelete(
      appConfig.baseUrl + s"/api/sample_data/${data}"
    )
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)

    if (sampleDataResponse.getStatusLine.getStatusCode != 204) {
      println("Deleting sample data failed")
    }
    this
  }

  def addSampleData(data: String): HttpHelper = {
    val sampleDataRequest = new HttpPost(
      appConfig.baseUrl + s"/api/sample_data/${data}"
    )
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", appConfig.buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)

    if (sampleDataResponse.getStatusLine.getStatusCode != 200) {
      println("Adding sample data failed")
    }
    this
  }

  def closeConnection(): Unit = {
    httpClient.close()
  }
}
