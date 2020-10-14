package org.kibanaLoadTest.helpers

import java.time.Instant

import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.xcontent.XContentType
import org.kibanaLoadTest.KibanaConfiguration


class ESWrapper(config: KibanaConfiguration) {

  def ingest(logFilePath: String, scenario: String): Unit = {
    val requests = LogParser.getRequests(logFilePath)

    val credentialsProvider = new BasicCredentialsProvider
    credentialsProvider.setCredentials(
      AuthScope.ANY,
      new UsernamePasswordCredentials(config.username, config.password)
    )

    val builder = RestClient.builder(
      new HttpHost(config.esHost, config.esPort, config.esScheme)
    ).setHttpClientConfigCallback(
      (httpClientBuilder: HttpAsyncClientBuilder) =>
        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
    ).setRequestConfigCallback(requestConfigBuilder => requestConfigBuilder
      .setConnectTimeout(30000)
      .setConnectionRequestTimeout(90000)
      .setSocketTimeout(90000))

    val client = new RestHighLevelClient(builder)
    val timestamp = Helper.convertDateToUTC(Instant.now.toEpochMilli)

    requests.par.foreach(request => {
      val jsonString: String =
        s"""
          |{
          | "timestamp": "${timestamp}",
          | "userId": ${request.userId},
          | "name": "${request.name}",
          | "requestSendStartTime": "${Helper.convertDateToUTC(request.requestSendStartTime)}",
          | "responseReceiveEndTime": "${Helper.convertDateToUTC(request.responseReceiveEndTime)}",
          | "status": "${request.status}",
          | "requestTime": ${request.requestTime},
          | "message": "${request.message}",
          | "version": "${config.buildVersion}",
          | "baseUrl": "${config.baseUrl}",
          | "securityEnabled": "${config.isSecurityEnabled}",
          | "scenario": "${scenario}"
          |}
      """.stripMargin

      client.index(new IndexRequest("request").source(jsonString, XContentType.JSON), RequestOptions.DEFAULT);

    })

    client.close()
  }
}
