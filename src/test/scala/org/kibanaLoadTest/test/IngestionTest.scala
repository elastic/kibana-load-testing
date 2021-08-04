package org.kibanaLoadTest.test

import java.io.File
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.circe.Json
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.{ESClient, Helper, LogParser}
import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable

class IngestionTest {

  val expCollectionSize = 799
  val expRequestString = "login - 1606920743240 - 1606920743948 - 708 - OK"

  @Test
  def parseLogsTest(): Unit = {
    val logFilePath: String = new File(
      Helper.getTargetPath + File.separator + "test-classes"
        + File.separator + "test" + File.separator
        + "simulation.log"
    ).getAbsolutePath
    val requests = LogParser.getRequestTimeline(logFilePath)
    assertEquals(
      expCollectionSize,
      requests.length,
      "Incorrect collection size"
    )
    assertEquals(
      expRequestString,
      requests.head.toString,
      "Incorrect content in first object"
    )
  }

  @Test
  def getSimulationClassTest(): Unit = {
    val className = LogParser.getSimulationClass(
      getClass.getResource("/test/simulation.log").getPath
    )
    assertEquals(className, "org.kibanaLoadTest.simulation.branch.DemoJourney")
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def ingestReportTest(): Unit = {
    val DATA_INDEX = "gatling-data"
    val host = System.getenv("HOST_FROM_VAULT")
    val username = System.getenv("USER_FROM_VAULT")
    val password = System.getenv("PASS_FROM_VAULT")
    val esConfig = new ESConfiguration(
      ConfigFactory.load
        .withValue("host", ConfigValueFactory.fromAnyRef(host))
        .withValue("username", ConfigValueFactory.fromAnyRef(username))
        .withValue("password", ConfigValueFactory.fromAnyRef(password))
    )

    val esClient = new ESClient(esConfig)
    val simLogFilePath = getClass.getResource("test/simulation.log").getPath
    val lastRunFilePath = getClass.getResource("test/lastRun.txt").getPath
    val metaJson = Helper.getMetaJson(lastRunFilePath, simLogFilePath)
    val requests = LogParser.getRequestTimeline(simLogFilePath)

    val requestJsonList = requests.par
      .map(request => {
        val requestJson = parse(request.toJsonString).getOrElse(Json.Null)
        val combinedRequestJson = requestJson.deepMerge(metaJson)
        combinedRequestJson
      })
      .toList
    esClient.ingest(DATA_INDEX, requestJsonList)
  }

  @Test
  def saveDeploymentConfigTest(): Unit = {
    val meta = Map(
      "deploymentId" -> "asdkjqwr9cuw4j23k",
      "baseUrl" -> "http://localhost:5620",
      "version" -> "8.0.0"
    )

    val filepath =
      Helper.getTargetPath + File.separator + "lastRun.txt"
    Helper.writeMapToFile(
      meta,
      filepath
    )

    val tempFile = new File(filepath)
    assertTrue(tempFile.exists, s"FIle $filepath does not exist")
  }
}
