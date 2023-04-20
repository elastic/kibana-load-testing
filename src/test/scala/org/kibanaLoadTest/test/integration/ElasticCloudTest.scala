package org.kibanaLoadTest.test.integration

import org.junit.jupiter.api.Assertions.{
  assertDoesNotThrow,
  assertFalse,
  assertTrue
}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.junit.jupiter.api.function.Executable
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{
  CloudHttpClient,
  DeploymentInfo,
  ESArchiver,
  ESClient,
  Helper,
  KbnClient,
  SimulationHelper
}
import org.kibanaLoadTest.ingest.Main.{
  DATA_INDEX,
  GLOBAL_STATS_FILENAME,
  GLOBAL_STATS_INDEX,
  SIMULATION_LOG_FILENAME,
  TEST_RUN_FILENAME,
  USERS_INDEX
}

import java.io.File
import java.nio.file.Paths

@TestInstance(Lifecycle.PER_CLASS)
class ElasticCloudTest {
  val expRequestRecordCount = 18
  val expUserRecordCount = 6
  val expRequestString = "login - 1628588469069 - 1628588469812 - 743 - OK"
  val expUserString = "1628588469042 - 1"
  val savedObjectPath = {
    Paths.get(getClass.getResource("/test/so.json").getPath)
  }
  val CLOUD_DEPLOY_CONFIG: String =
    System.getProperty("deploymentConfig", "config/deploy/default.conf")
  val STACK_VERSION: String =
    System.getProperty("stackVersion", "8.7.0")
  var config: KibanaConfiguration = null

  @BeforeAll
  def tearUp(): Unit = {
    config = SimulationHelper
      .createDeployment(
        stackVersion = STACK_VERSION,
        deployFile = CLOUD_DEPLOY_CONFIG
      )
  }

  @AfterAll
  def tearDown(): Unit = {
    new CloudHttpClient().deleteDeployment(config.deploymentId.get)
  }

  @Test
  def ingestGatlingReportTest(): Unit = {
    val esClient = ESClient.getInstance(
      Helper.parseUrl(config.esUrl),
      config.username,
      config.password
    )
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
  def kbnClientLoadAndUnloadDataTest() = {
    val client = new KbnClient(
      config.baseUrl,
      config.username,
      config.password,
      config.providerName,
      config.providerType
    )
    val loadClosure: Executable = () => client.load(savedObjectPath)
    val unloadClosure: Executable = () => client.unload(savedObjectPath)
    assertDoesNotThrow(loadClosure, "client.load throws exception")
    assertDoesNotThrow(unloadClosure, "client.unload throws exception")
  }

  @Test
  def esArchiverLoadAndUnloadDataTest(): Unit = {
    val archivePath = getClass.getResource("/test/es_archive").getPath
    val esArchiver = new ESArchiver(config)
    val loadClosure: Executable = () => esArchiver.load(Paths.get(archivePath))
    val unloadClosure: Executable = () =>
      esArchiver.unload(Paths.get(archivePath))
    assertDoesNotThrow(loadClosure, "esArchiver.load throws exception")
    assertDoesNotThrow(unloadClosure, "esArchiver.unload throws exception")
  }

  @Test
  def waitForClusterToStartTest(): Unit = {
    val deployment =
      DeploymentInfo(
        "fakeIt",
        "user",
        "password",
        List("kibana", "elasticsearch")
      )
    val deploymentId = "fakeIt"
    val timeout = 100
    val interval = 20
    def getFailedStatus(deployment: DeploymentInfo): Map[String, String] = {
      // completely ignore id
      Map(
        "kibana" -> "initializing",
        "elasticsearch" -> "started"
      )
    }

    val cloudClient = new CloudHttpClient
    val isReady = cloudClient.waitForClusterToStart(
      deployment,
      getFailedStatus,
      timeout,
      interval
    )

    assertFalse(isReady)
  }

  @Test
  def getDeploymentsTest(): Unit = {
    val cloudClient = new CloudHttpClient
    val items = cloudClient.getDeployments
    assertTrue(!items.isEmpty)
  }

  @Test
  def getLatestAvailableVersionTest(): Unit = {
    val cloudClient = new CloudHttpClient
    val version = cloudClient.getLatestAvailableVersion("7.", "general-purpose")
    assertTrue(version.get.startsWith("7."))
  }
}
