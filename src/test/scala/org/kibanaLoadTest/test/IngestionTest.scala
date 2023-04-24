package org.kibanaLoadTest.test

import java.io.File
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{ESArchiver, Helper, HttpHelper, LogParser, ResponseParser}
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
    kbnMock = new KibanaMockServer(5601)
    kbnMock.createKibanaIndexPageCallback()
    kbnMock.createKibanaStatusCallback()
    kbnMock.createSuccessfulLoginCallback()
    esMock = new ESMockServer(9300)
    esMock.createStatusCallback()
    config = new KibanaConfiguration(
      Helper.readResourceConfigFile("test/local.conf")
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
}
