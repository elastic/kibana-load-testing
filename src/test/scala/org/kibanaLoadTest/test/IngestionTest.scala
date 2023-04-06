package org.kibanaLoadTest.test

import java.io.File
import org.junit.jupiter.api.Assertions.{assertDoesNotThrow, assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.function.Executable
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.getReportFolderPaths
import org.kibanaLoadTest.helpers.{ESArchiver, ESClient, Helper, HttpHelper, LogParser, ResponseParser}
import org.kibanaLoadTest.ingest.Main.{GLOBAL_STATS_FILENAME, GLOBAL_STATS_INDEX, SIMULATION_LOG_FILENAME, TEST_RUN_FILENAME, USERS_INDEX, logger}
import org.kibanaLoadTest.test.mocks.{ESMockServer, KibanaMockServer}

import java.nio.file.Paths

@TestInstance(Lifecycle.PER_CLASS)
class IngestionTest {
  private var config: KibanaConfiguration = null
  private var helper: HttpHelper = null
  val expRequestRecordCount = 18
  val expUserRecordCount = 6
  val expRequestString = "login - 1628588469069 - 1628588469812 - 743 - OK"
  val expUserString = "1628588469042 - 1"

  var kbnMock: KibanaMockServer = null
  var esMock: ESMockServer = null

  @BeforeAll
  def tearUp(): Unit = {
    kbnMock = new KibanaMockServer(5620)
    kbnMock.createKibanaStatusCallback()
    esMock = new ESMockServer(9220)
    esMock.createStatusCallback()
    config = new KibanaConfiguration(
      Helper.readResourceConfigFile("config/local.conf")
    )
    helper = new HttpHelper(config)
  }

  @AfterAll
  def tearDown(): Unit = {
    kbnMock.destroy()
    esMock.destroy()
  }

  @Test
  def parseSimulationLogTest(): Unit = {
    val logFilePath: String = new File(
      Helper.getTargetPath + File.separator + "test-classes"
        + File.separator + "test" + File.separator
        + "simulation.log"
    ).getAbsolutePath
    val (requestsTimeline, concurrentUsers, usersStats) =
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

  /**
    * This test should be run locally against real Elasticsearch instance
    */
  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def ingestReportTest(): Unit = {
    val DATA_INDEX = "gatling-data"
    val host: String = System.getenv("HOST_FROM_VAULT")
    val url = Helper.parseUrl(host)
    val username: String = System.getenv("USER_FROM_VAULT")
    val password: String = System.getenv("PASS_FROM_VAULT")

    val esClient = ESClient.getInstance(url, username, password)
    val reportFolders = getReportFolderPaths

    logger.info(s"Found ${reportFolders.length} Gatling reports")
    val testPath =
      Helper.getTargetPath + File.separator + "test-classes" + File.separator + "test" + File.separator
    val simLogFilePath: String = new File(
      testPath + SIMULATION_LOG_FILENAME
    ).getAbsolutePath
    val testRunFilePath: String = new File(
      testPath + TEST_RUN_FILENAME
    ).getAbsolutePath
    val statsFilePath: String = new File(
      testPath + GLOBAL_STATS_FILENAME
    ).getAbsolutePath

    val (requestsArray, concurrentUsersArray, combinedStatsArray) =
      Helper.prepareDocsForIngestion(
        statsFilePath,
        simLogFilePath,
        testRunFilePath
      )

    esClient.bulk(GLOBAL_STATS_INDEX, combinedStatsArray, 100)
    esClient.bulk(DATA_INDEX, requestsArray, 100)
    esClient.bulk(USERS_INDEX, concurrentUsersArray, 100)

    esClient.closeConnection()
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
    val esArchiver = new ESArchiver(config)
    val indexArray = esArchiver.readDataFromFile(Paths.get(mappingsFilePath))
    val docsArray = esArchiver.readDataFromFile(Paths.get(dataFilePath))
    assertEquals(1, indexArray.length, "Indexes count is incorrect")
    assertEquals(111396, docsArray.length, "Docs count is incorrect")
  }

  /**
    * This test should be run locally against real Elasticsearch instance
    */
  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def ESArchiverIngestTest(): Unit = {
    val archivePath = getClass.getResource("/test/es_archive").getPath
    val esArchiver = new ESArchiver(config)
    val loadClosure: Executable = () => esArchiver.load(Paths.get(archivePath))
    val unloadClosure: Executable = () =>
      esArchiver.unload(Paths.get(archivePath))
    assertDoesNotThrow(loadClosure, "esArchiver.load throws exception")
    assertDoesNotThrow(unloadClosure, "esArchiver.unload throws exception")
  }
}
