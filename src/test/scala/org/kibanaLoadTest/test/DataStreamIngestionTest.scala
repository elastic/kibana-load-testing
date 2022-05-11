package org.kibanaLoadTest.test

import java.io.File
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.circe.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.ESClient
import org.kibanaLoadTest.helpers.Helper
import org.kibanaLoadTest.ingest.Main.GLOBAL_STATS_FILENAME
import org.kibanaLoadTest.ingest.Main.RESPONSE_LOG_FILENAME
import org.kibanaLoadTest.ingest.Main.SIMULATION_LOG_FILENAME
import org.kibanaLoadTest.ingest.Main.TEST_RUN_FILENAME

import java.io.File
import java.nio.file.Paths
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class DataStreamIngestionTest {

  val simLogFilePath = new File(s"${fixturesPath()}/simulation.log.txt").getAbsolutePath
  val testRunFilePath = new File(s"${fixturesPath()}/testRun.txt").getAbsolutePath
  val responseFilePath = new File(s"${fixturesPath()}/response.log.txt").getAbsolutePath
  val statsFilePath = new File(s"${fixturesPath()}/global_stats.json").getAbsolutePath
  val DEFAULT_INTEGRATION_TEST_DATA_STREAM = "integration-test-gatling-data"

  @Test
  def dataStreamBulkIngestTest(): Unit = {
    showFrom()
    val client = new ESClient(config())

    val (requestsArray, _, _) =
      Helper.prepareDocsForIngestion(statsFilePath, simLogFilePath, responseFilePath, testRunFilePath)
    logSome(1)(requestsArray)

    val writeData = writeAndAssert(client)(requestsArray)_
    dataStreamName() match {
      case Success(x) => writeData(x)
      case Failure(_) => writeData(DEFAULT_INTEGRATION_TEST_DATA_STREAM)
    }
  }

  def writeAndAssert(client: ESClient)(xs: Array[Json])(streamName: String): Unit = {
    val docsCountWith = docsCount(client)(streamName)
    val beforeDocsCount: Long = docsCountWith()
    write(client)(xs)(streamName)
    val afterDocsCount: Long = docsCountWith()
    assert(afterDocsCount - beforeDocsCount == 10)
    client.Instance.closeConnection()
  }

  def fixturesPath() =
    "%s/test/resources/integration-test/data-stream/fixtures"
      .format(Helper.getSrcPath)

  def showFrom() = {
    val xs = simLogFilePath :: testRunFilePath :: responseFilePath :: statsFilePath :: Nil
    println(s"\n### Ingesting from:")
    xs foreach println
    println
  }
  def docsCount(client: ESClient)(indexName: String) =
    () => client.Instance.count(indexName)

  def write(client: ESClient)(xs: Array[Json])(indexName: String): Unit =
    client.Instance.bulk(indexName, xs.take(10), 100, true)

  def dataStreamName():Try[String] =
    Try(System.getenv("DATA_STREAM_NAME"))

  def logSome(x: Int)(xs: Array[Json]): Unit = {
    println(s"### Logging [$x] record(s)")
    xs.take(x) foreach println
  }

  def config(): ESConfiguration = {
    val host = System.getenv("HOST_FROM_VAULT")
    val username = System.getenv("USER_FROM_VAULT")
    val password = System.getenv("PASS_FROM_VAULT")
    new ESConfiguration(
      ConfigFactory.load
        .withValue("host", ConfigValueFactory.fromAnyRef(host))
        .withValue("username", ConfigValueFactory.fromAnyRef(username))
        .withValue("password", ConfigValueFactory.fromAnyRef(password))
    )
  }

}
