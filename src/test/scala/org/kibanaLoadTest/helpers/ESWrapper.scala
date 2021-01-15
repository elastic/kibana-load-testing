package org.kibanaLoadTest.helpers

import java.nio.file.{Files, Paths}
import java.time.Instant

import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.{
  RequestOptions,
  RestClient,
  RestHighLevelClient
}
import org.elasticsearch.common.xcontent.XContentType
import org.kibanaLoadTest.ESConfiguration
import org.slf4j.{Logger, LoggerFactory}
import scala.collection.parallel.CollectionConverters._

class ESWrapper(config: ESConfiguration) {
  val logger: Logger = LoggerFactory.getLogger("ES_Client")
  val indexName = "gatling-data"

  def ingest(logFilePath: String, metaFilePath: String): Unit = {
    if (!Files.exists(Paths.get(logFilePath))) {
      throw new RuntimeException(
        s"Gatling report file '${logFilePath}' is not found"
      )
    }
    if (!Files.exists(Paths.get(metaFilePath))) {
      throw new RuntimeException(
        s"Deployment meta file '${metaFilePath}' is not found"
      )
    }

    val requests = LogParser.getRequests(logFilePath)
    val simulationClass = LogParser.getSimulationClass(logFilePath)
    val meta = Helper.readFileToMap(metaFilePath)

    val credentialsProvider = new BasicCredentialsProvider
    credentialsProvider.setCredentials(
      AuthScope.ANY,
      new UsernamePasswordCredentials(config.username, config.password)
    )

    val builder = RestClient
      .builder(
        HttpHost.create(config.host)
      )
      .setHttpClientConfigCallback(
        (httpClientBuilder: HttpAsyncClientBuilder) =>
          httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
      )
      .setRequestConfigCallback(requestConfigBuilder =>
        requestConfigBuilder
          .setConnectTimeout(30000)
          .setConnectionRequestTimeout(90000)
          .setSocketTimeout(90000)
      )

    logger.info(s"Login to ES instance: ${config.host}")
    val client = new RestHighLevelClient(builder)
    val timestamp = Helper.convertDateToUTC(Instant.now.toEpochMilli)

    logger.info(s"Ingesting report to stats cluster: ${requests.size} records")
    requests.par.foreach(request => {
      val jsonString: String =
        s"""
          |{
          | "timestamp": "${timestamp}",
          | "name": "${request.name}",
          | "requestSendStartTime": "${Helper.convertDateToUTC(
          request.requestSendStartTime
        )}",
          | "responseReceiveEndTime": "${Helper.convertDateToUTC(
          request.responseReceiveEndTime
        )}",
          | "status": "${request.status}",
          | "requestTime": ${request.requestTime},
          | "message": "${request.message}",
          | "version": "${meta("version")}",
          | "buildHash": "${meta("buildHash")}",
          | "buildNumber": ${meta("buildNumber")},
          | "branch": ${meta("branch")},
          | "isSnapshotBuild": ${meta("isSnapshotBuild")}
          | "baseUrl": "${meta("baseUrl")}",
          | "scenario": "${simulationClass}"
          |}
      """.stripMargin

      client.index(
        new IndexRequest(indexName).source(jsonString, XContentType.JSON),
        RequestOptions.DEFAULT
      );

    })

    logger.info("Ingestion is completed, closing connection")
    client.close()
  }
}
