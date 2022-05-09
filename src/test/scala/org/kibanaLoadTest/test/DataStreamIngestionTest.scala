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

import scala.util.Failure
import scala.util.Success
import scala.util.Try

class DataStreamIngestionTest {

  val testPath = s"${Helper.getTargetPath}${File.separator}test-classes${File.separator}test${File.separator}"
  val simLogFilePath: String = new File(testPath + SIMULATION_LOG_FILENAME).getAbsolutePath
  val testRunFilePath: String = new File(testPath + TEST_RUN_FILENAME).getAbsolutePath
  val responseFilePath: String = new File(testPath + RESPONSE_LOG_FILENAME).getAbsolutePath
  val statsFilePath: String = new File(testPath + GLOBAL_STATS_FILENAME).getAbsolutePath
  val DEFAULT_INTEGRATION_TEST_DATA_STREAM = "integration-test-gatling-data"

  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def dataStreamBulkIngestTest(): Unit = {
    val client = new ESClient(config())

    val (requestsArray, concurrentUsersArray, combinedStatsArray) =
      Helper.prepareDocsForIngestion(statsFilePath, simLogFilePath, responseFilePath, testRunFilePath)
    logSome(0)(requestsArray)

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

  def docsCount(client: ESClient)(indexName: String) =
    () => client.Instance.count(indexName)

  def write(client: ESClient)(xs: Array[Json])(indexName: String): Unit =
    client.Instance.bulk(indexName, xs.take(10), 100, true)

  def dataStreamName():Try[String] =
    Try(System.getenv("DATA_STREAM_NAME"))

  def logSome(x: Int)(xs: Array[Json]): Unit =
    xs.take(x).foreach(x => println(s"### Item: ${x}"))

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
