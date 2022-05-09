package org.kibanaLoadTest.helpers

import io.circe.Json
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.xcontent.XContentType
import org.kibanaLoadTest.ESConfiguration
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

case class Chunk(request: BulkRequest, size: Int)

class ESClient(config: ESConfiguration) {

  private var client: RestHighLevelClient = null
  private val logger: Logger = LoggerFactory.getLogger("ES_Client")

  private def createInstance(config: ESConfiguration): RestHighLevelClient = {
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

  object Instance extends ESClient(config) {
    private val BULK_SIZE_DEFAULT = 100
    private val BULK_SIZE =
      Option(System.getenv("INGEST_BULK_SIZE")).map(_.toInt).getOrElse(300)
    client = createInstance(config)

    def bulk(
        indexName: String,
        jsonArray: Array[Json],
        chunkSize: Int = BULK_SIZE,
        immediate: Boolean = false
    ): Unit = {
      try {
        logger.info(s"Ingesting to '$indexName' index: ${jsonArray.length} docs")
        val startTime = new Date()
        val bulkSize =
          if (indexName == "gatling-data") BULK_SIZE_DEFAULT else chunkSize
        val it = jsonArray.grouped(bulkSize)
        val bulkBuffer = scala.collection.mutable.ArrayBuffer.empty[Chunk]
        while (it.hasNext) {
          val chunk = it.next()
          val bulkReq = new BulkRequest()
          if (immediate) bulkReq.setRefreshPolicy(RefreshPolicy.IMMEDIATE)
          val chunkSize = chunk.length
          var i = 0
          while (i < chunkSize) {
            val request = new IndexRequest(indexName)
            request.opType("create")
            bulkReq.add(
              request
                .source(chunk(i).toString(), XContentType.JSON)
            )
            i += 1
          }
          bulkBuffer += Chunk(bulkReq, chunk.length)
        }

        val bulkBufferSize = bulkBuffer.length
        var j = 0
        while (j < bulkBufferSize) {
          val bulkResponse =
            client.bulk(bulkBuffer(j).request, RequestOptions.DEFAULT)
          bulkResponse.getTook.toString
          if (bulkResponse.hasFailures) {
            logger.error("Ingested with failures")
            logFailures(bulkResponse)
          }
          j += 1
        }
        val diff = Math.abs(new Date().getTime - startTime.getTime)
        val min = TimeUnit.MILLISECONDS.toMinutes(diff)
        val sec = TimeUnit.MILLISECONDS.toSeconds(diff - min * 60 * 1000)
        logger.info(s"Ingestion is completed. Took: $min minutes $sec seconds")
      } catch {
        case e: IOException =>
          logger.error(
            s"Exception occurred during ingestion:\n ${e.toString}"
          )
      }
    }

    def createIndex(indexName: String, source: Json): Unit = {
      val request = new CreateIndexRequest(indexName)
      request.source(source.toString(), XContentType.JSON)
      try {
        client.indices().create(request, RequestOptions.DEFAULT)
      } catch {
        case e: IOException =>
          logger.error(
            s"Exception occurred during index creation:\n ${e.toString}"
          )
      }
    }

    def closeConnection(): Unit =
      if (client != null) {
        logger.info("Closing connection")
        client.close()
      }

    def count(indexName: String): Long =
      client.count(new CountRequest(indexName), RequestOptions.DEFAULT).getCount


    def logFailures(res: BulkResponse): Unit =
      res.forEach(x => if (x.isFailed) logger.info(s"### Failure: ${x.getFailure}"))

  }
}
