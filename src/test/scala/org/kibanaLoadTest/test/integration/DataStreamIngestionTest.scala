package org.kibanaLoadTest.test.integration

import io.circe.Json
import org.junit.jupiter.api.{Disabled, Test}
import org.kibanaLoadTest.helpers.{ESClient, Helper}

import java.io.File

class DataStreamIngestionTest {
    val simLogFilePath = new File(s"${fixturesPath()}/simulation.log.txt").getAbsolutePath
    val testRunFilePath = new File(s"${fixturesPath()}/testRun.txt").getAbsolutePath
    val statsFilePath = new File(s"${fixturesPath()}/global_stats.json").getAbsolutePath
    val DEFAULT_INTEGRATION_TEST_DATA_STREAM = "integration-test-gatling-data"

    @Disabled("Potentially we no longer needed or `ingestGatlingReportTest` to be adjusted")
    @Test
    def dataStreamBulkIngestTest(): Unit = {
      showFrom()
      val client = getEsClient()

      val (requestsArray, _, _) =
        Helper.prepareDocsForIngestion(statsFilePath, simLogFilePath, testRunFilePath)
      logSome(1)(requestsArray)

      val writeData = writeAndAssert(client)(requestsArray)_
      dataStreamName()
        .fold(
          _ => writeData(DEFAULT_INTEGRATION_TEST_DATA_STREAM),
          writeData
        )
    }

    def writeAndAssert(client: ESClient)(xs: Array[Json])(streamName: String): Unit = {
      val docsCountWith = docsCount(client)(streamName)
      val beforeDocsCount: Long = docsCountWith()
      write(client)(xs)(streamName)
      val afterDocsCount: Long = docsCountWith()
      assert(afterDocsCount - beforeDocsCount == 10)
      client.closeConnection()
    }

    def fixturesPath = () =>
      "%s/test/resources/integration-test/data-stream/fixtures"
        .format(Helper.getSrcPath)

    def showFrom: () => Unit = () => {
      val xs = simLogFilePath :: testRunFilePath :: statsFilePath :: Nil
      println(s"\n### Ingesting from:")
      xs foreach println
      println()
    }

    def docsCount = (client: ESClient) => (indexName: String) =>
      () => client.count(indexName)

    def write = (client: ESClient) => (xs: Array[Json]) => (indexName: String) =>
      client.bulk(indexName, xs.take(10), 100)

    def dataStreamName(): Either[String, String] = {
      val res = System.getenv("DATA_STREAM_NAME")
      if (res == null) Left("DATA_STREAM_NAME not found") else Right(res)
    }

    def logSome = (x: Int) => (xs: Array[Json]) => {
      println(s"### Logging [$x] record(s)")
      xs.take(x) foreach println
    }

    def getEsClient = () => {
      val host = s"https://${System.getenv("HOST_FROM_VAULT")}"
      val username = System.getenv("USER_FROM_VAULT")
      val password = System.getenv("PASS_FROM_VAULT")
      new ESClient(host, username, password)
    }
}
