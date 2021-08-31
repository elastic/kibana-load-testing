package org.kibanaLoadTest.helpers

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
import io.circe.Json
import org.elasticsearch.action.bulk.BulkRequest

import java.io.IOException

class ESClient(config: ESConfiguration) {
  val logger: Logger = LoggerFactory.getLogger("ES_Client")
  val BULK_SIZE =
    Option(System.getenv("INGEST_BULK_SIZE")).map(_.toInt).getOrElse(200)

  def ingest(indexName: String, jsonList: List[Json]): Unit = {
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

    try {
      logger.info(s"Ingesting to stats cluster: ${jsonList.size} docs")

      val it = jsonList.grouped(BULK_SIZE)
      val bulkBuffer = scala.collection.mutable.ListBuffer.empty[BulkRequest]
      while (it.hasNext) {
        val chunk = it.next()
        val bulkReq = new BulkRequest()
        chunk.foreach(json => {
          bulkReq.add(
            new IndexRequest(indexName)
              .source(json.toString(), XContentType.JSON)
          )
        })
        bulkBuffer += bulkReq
      }

      bulkBuffer.foreach(bulkReq => {
        val bulkResponse = client.bulk(bulkReq, RequestOptions.DEFAULT)
        bulkResponse.getTook.toString
        logger.info(s"Bulk ingested within: ${bulkResponse.getTook.toString}")
        if (bulkResponse.hasFailures) {
          logger.error("Ingested with failures")
        }
      })
      logger.info("Ingestion is completed")
    } catch {
      case e: IOException =>
        logger.error(
          s"Exception occurred during ingestion:\n ${e.toString}"
        )
    } finally {
      logger.info("Closing connection")
      client.close()
    }
  }
}
