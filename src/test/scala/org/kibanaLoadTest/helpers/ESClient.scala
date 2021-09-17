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
import org.elasticsearch.client.indices.CreateIndexRequest

import java.io.IOException

class ESClient(config: ESConfiguration) {
  val logger: Logger = LoggerFactory.getLogger("ES_Client")
  val BULK_SIZE_DEFAULT = 100
  val BULK_SIZE =
    Option(System.getenv("INGEST_BULK_SIZE")).map(_.toInt).getOrElse(300)

  def getClient(): RestHighLevelClient = {
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
    new RestHighLevelClient(builder)
  }

  def ingest(indexName: String, jsonList: List[Json]): Unit = {
    val client = getClient()

    try {
      logger.info(s"Ingesting to stats cluster: ${jsonList.size} docs")

      val bulkSize =
        if (indexName == "gatling-data") BULK_SIZE_DEFAULT else BULK_SIZE
      val it = jsonList.grouped(bulkSize)
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

  def createIndex(indexName: String, source: Json): Unit = {
    val client = getClient()
    val request = new CreateIndexRequest(indexName)
    request.source(source.toString(), XContentType.JSON)
    try {
      val createIndexResponse =
        client.indices().create(request, RequestOptions.DEFAULT);
    } catch {
      case e: IOException =>
        logger.error(
          s"Exception occurred during index creation:\n ${e.toString}"
        )
    } finally {
      logger.info("Closing connection")
      client.close()
    }
  }
}
