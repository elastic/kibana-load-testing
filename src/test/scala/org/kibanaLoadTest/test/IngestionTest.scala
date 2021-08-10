package org.kibanaLoadTest.test

import com.google.gson.Gson

import java.io.File
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.circe.Json
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.{ESClient, Helper, LogParser, ResponseParser}
import org.kibanaLoadTest.ingest.Main.USERS_INDEX

import scala.collection.parallel.CollectionConverters.IterableIsParallelizable

class IngestionTest {

  val expRequestRecordCount = 942
  val expUserRecordCount = 56
  val expRequestString = "login - 1628082216005 - 1628082219329 - 3324 - OK"
  val expUserString = "1628082215986 - 1"

  @Test
  def parseSimulationLogTest(): Unit = {
    val logFilePath: String = new File(
      Helper.getTargetPath + File.separator + "test-classes"
        + File.separator + "test" + File.separator
        + "simulation.log"
    ).getAbsolutePath
    val (requestsTimeline, concurrentUsers) =
      LogParser.parseSimulationLog(logFilePath)
    assertEquals(
      expRequestRecordCount,
      requestsTimeline.length,
      "Incorrect request record count"
    )
    assertEquals(
      expRequestString,
      requestsTimeline.head.toString,
      "Incorrect content in first object"
    )
    assertEquals(
      expUserRecordCount,
      concurrentUsers.length,
      "Incorrect users record count"
    )
    assertEquals(
      expUserString,
      concurrentUsers.head.toString,
      "Incorrect content in first object"
    )
  }

  @Test
  def parseResponseLogTest(): Unit = {
    val responseFilePath = getClass.getResource("/test/response.log").getPath
    val responses = ResponseParser.getRequests(responseFilePath)
    assertEquals(
      expRequestRecordCount,
      responses.length,
      "Incorrect response record count"
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
    val simLogFilePath = getClass.getResource("/test/simulation.log").getPath
    val lastRunFilePath = getClass.getResource("/test/lastRun.txt").getPath
    val responseFilePath = getClass.getResource("/test/response.log").getPath
    val metaJson = Helper.getMetaJson(lastRunFilePath, simLogFilePath)
    val (requestsTimeline, concurrentUsers) =
      LogParser.parseSimulationLog(simLogFilePath)
    val responses = ResponseParser.getRequests(responseFilePath)
    for (i <- 0 to responses.length - 1) {
      val value = responses(i)
      responses(i) = value.copy(
        requestSendStartTime = requestsTimeline(i).requestSendStartTime,
        responseReceiveEndTime = requestsTimeline(i).responseReceiveEndTime,
        message = requestsTimeline(i).message,
        requestTime = requestsTimeline(i).requestTime
      )
    }

    val requestJsonList = responses.par
      .map(request => {
        val gson = new Gson
        val requestJson = parse(gson.toJson(request)).getOrElse(Json.Null)
        if (requestJson == Json.Null) {
          println("failed to parse json")
        }
        val combinedRequestJson = requestJson.deepMerge(metaJson)
        combinedRequestJson
      })
      .toList
    esClient.ingest(DATA_INDEX, requestJsonList)

    val concurrentUsersJsonList = concurrentUsers.map(stat => {
      val gson = new Gson
      val json = parse(gson.toJson(stat)).getOrElse(Json.Null)
      json.deepMerge(metaJson)
    })

    esClient.ingest(USERS_INDEX, concurrentUsersJsonList)
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
