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
import org.kibanaLoadTest.helpers.{
  ESArchiver,
  ESClient,
  Helper,
  LogParser,
  ResponseParser
}
import org.kibanaLoadTest.ingest.Main.USERS_INDEX

import scala.collection.parallel.CollectionConverters.IterableIsParallelizable

class IngestionTest {

  val expRequestRecordCount = 18
  val expUserRecordCount = 6
  val expRequestString = "login - 1628588469069 - 1628588469812 - 743 - OK"
  val expUserString = "1628588469042 - 1"

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
    val responseFilePath: String = new File(
      Helper.getTargetPath + File.separator + "test-classes"
        + File.separator + "test" + File.separator
        + "response.log"
    ).getAbsolutePath
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
    val testPath =
      Helper.getTargetPath + File.separator + "test-classes" + File.separator + "test" + File.separator
    val simLogFilePath: String = new File(
      testPath + "simulation.log"
    ).getAbsolutePath
    val lastRunFilePath: String = new File(
      testPath + "lastRun.txt"
    ).getAbsolutePath
    val responseFilePath: String = new File(
      testPath + "response.log"
    ).getAbsolutePath
    val metaJson = Helper.getMetaJson(lastRunFilePath, simLogFilePath)
    val (requestsTimeline, concurrentUsers) =
      LogParser.parseSimulationLog(simLogFilePath)
    val responses = ResponseParser.getRequests(responseFilePath)
    for (i <- 0 to responses.length - 1) {
      responses(i) = responses(i).copy(
        requestSendStartTime = requestsTimeline(i).requestSendStartTime,
        responseReceiveEndTime = requestsTimeline(i).responseReceiveEndTime,
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
    esClient.Instance.bulk(DATA_INDEX, requestJsonList)

    val concurrentUsersJsonList = concurrentUsers.map(stat => {
      val gson = new Gson
      val json = parse(gson.toJson(stat)).getOrElse(Json.Null)
      json.deepMerge(metaJson)
    })

    esClient.Instance.bulk(USERS_INDEX, concurrentUsersJsonList)
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

  @Test
  def ESArchiverParseTest(): Unit = {
    val mappingsFilePath =
      getClass.getResource("/test/es_archive/mappings.json").getPath
    val dataFilePath =
      getClass.getResource("/test/es_archive/data.json.gz").getPath
    val indexArray = ESArchiver.readDataFromFile(mappingsFilePath)
    val docsArray = ESArchiver.readDataFromFile(dataFilePath)

    assertEquals(1, indexArray.length, "Indexes count is incorrect")
    assertEquals(111396, docsArray.length, "Docs count is incorrect")
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def ESArchiverIngestTest(): Unit = {
    val mappingsFilePath =
      getClass.getResource("/test/es_archive/mappings.json").getPath
    val dataFilePath =
      getClass.getResource("/test/es_archive/data.json.gz").getPath
    val indexArray = ESArchiver.readDataFromFile(mappingsFilePath)
    val docsArray = ESArchiver.readDataFromFile(dataFilePath)

    val host = System.getenv("HOST_FROM_VAULT")
    val username = System.getenv("USER_FROM_VAULT")
    val password = System.getenv("PASS_FROM_VAULT")

    val esConfig = new ESConfiguration(
      ConfigFactory.load
        .withValue("host", ConfigValueFactory.fromAnyRef(host))
        .withValue("username", ConfigValueFactory.fromAnyRef(username))
        .withValue("password", ConfigValueFactory.fromAnyRef(password))
    )

    val client = new ESClient(esConfig)

    indexArray.foreach(item => {
      client.Instance.createIndex(item.index + "6", item.source)
      println(s"${item.index} index was created")
    })

    client.Instance.bulk(
      indexArray(0).index,
      docsArray.map(doc => doc.source).toList,
      chunkSize = 1000
    )

    client.Instance.closeConnection()
  }
}
