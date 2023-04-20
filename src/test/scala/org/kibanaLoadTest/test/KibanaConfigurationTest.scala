package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{Helper, SimulationHelper, Version}
import org.kibanaLoadTest.test.mocks.KibanaMockServer

import java.nio.file.Paths

@TestInstance(Lifecycle.PER_CLASS)
class KibanaConfigurationTest {
  val port = 5601
  val version = "8.8.8"
  val buildHash = "xyz"
  val buildNumber = 1234
  val buildSnapshot = true
  val buildVersion = "8.8.8-SNAPSHOT"
  var kbnMock: KibanaMockServer = null

  def verifyConfiguration(
      config: KibanaConfiguration,
      kibanaUrl: String,
      esUrl: String,
      username: String,
      password: String,
      providerName: String,
      providerType: String,
      version: String,
      buildVersion: String,
      buildHash: String,
      buildNumber: Long,
      isSnapshotBuild: Boolean,
      deploymentId: Option[String],
      deleteDeploymentOnFinish: Boolean
  ): Unit = {
    assertEquals(config.baseUrl, kibanaUrl, "Incorrect 'baseUrl'")
    assertEquals(config.esUrl, esUrl, "Incorrect 'esUrl'")
    assertEquals(config.username, username, "Incorrect 'username'")
    assertEquals(config.password, password, "Incorrect 'password'")
    assertEquals(config.providerName, providerName, "Incorrect 'providerName'")
    assertEquals(config.providerType, providerType, "Incorrect 'providerType'")
    assertEquals(config.version, version, "Incorrect 'version'")
    assertEquals(config.buildVersion, buildVersion, "Incorrect 'buildVersion'")
    assertEquals(config.buildHash, buildHash, "Incorrect 'buildHash'")
    assertEquals(config.buildNumber, buildNumber, "Incorrect 'buildNumber'")
    assertEquals(
      config.isSnapshotBuild,
      isSnapshotBuild,
      "Incorrect 'isSnapshotBuild'"
    )
    assertEquals(
      config.deploymentId.getOrElse(None),
      deploymentId.getOrElse(None),
      "Incorrect 'deploymentId'"
    )
    assertEquals(
      config.deleteDeploymentOnFinish,
      deleteDeploymentOnFinish,
      "Incorrect 'deleteDeploymentOnFinish'"
    )
    assertEquals(
      config.loginPayload,
      s"""{"providerType":"$providerType","providerName":"$providerName","currentURL":"$kibanaUrl/login","params":{"username":"$username","password":"$password"}}""",
      "Incorrect 'loginPayload'"
    )
  }

  @BeforeAll
  def tearUp(): Unit = {
    kbnMock = new KibanaMockServer(port)
    kbnMock.createSuccessfulLoginCallback()
  }

  @AfterAll
  def tearDown(): Unit = {
    kbnMock.destroy()
  }

  @Test
  def readConfigFromFileTest(): Unit = {
    kbnMock.createKibanaIndexPageCallback(version = buildVersion)
    kbnMock.createKibanaStatusCallback(
      build_hash = buildHash,
      build_number = buildNumber,
      build_snapshot = buildSnapshot,
      number = version
    )
    val configObject = Helper.readResourceConfigFile("test/local.conf")
    val config = new KibanaConfiguration(configObject)

    verifyConfiguration(
      config,
      configObject.getString("host.kibana"),
      configObject.getString("host.es"),
      configObject.getString("auth.username"),
      configObject.getString("auth.password"),
      configObject.getString("auth.providerName"),
      configObject.getString("auth.providerType"),
      version,
      buildVersion,
      buildHash,
      buildNumber,
      isSnapshotBuild = true,
      deploymentId = None,
      deleteDeploymentOnFinish = false
    )
  }

  @Test
  def createConfigWithConstructorTest(): Unit = {
    val kibanaUrl = s"http://localhost:$port"
    val esUrl = s"http://localhost:9300"
    val username = "user1"
    val password = "test"
    val providerType = "baseType"
    val providerName = "baseProvider"

    kbnMock.createKibanaIndexPageCallback(version = buildVersion)
    kbnMock.createKibanaStatusCallback(
      build_hash = buildHash,
      build_number = buildNumber,
      build_snapshot = buildSnapshot,
      number = version
    )

    val config = new KibanaConfiguration(
      kibanaHost = kibanaUrl,
      esHost = esUrl,
      username = username,
      password = password,
      providerType = providerType,
      providerName = providerName
    )

    verifyConfiguration(
      config,
      kibanaUrl,
      esUrl,
      username,
      password,
      providerName,
      providerType,
      version,
      buildVersion,
      buildHash,
      buildNumber,
      isSnapshotBuild = true,
      deploymentId = None,
      deleteDeploymentOnFinish = false
    )
  }

  @Test
  def createConfigForCloudDeploymentTest(): Unit = {
    val deploymentFilePath =
      Paths.get(getClass.getResource("/test/deployment.txt").getPath).toString
    val buildHash = "zxcvbnm"
    val buildNumber = 1234567890
    val meta = Helper.readFileToMap(deploymentFilePath)
    val deploymentVersion = new Version(meta("version").toString)
    kbnMock.createKibanaIndexPageCallback(version = deploymentVersion.value)
    kbnMock.createKibanaStatusCallback(
      build_hash = buildHash,
      build_number = buildNumber,
      number = deploymentVersion.version
    )

    val config = SimulationHelper.useExistingDeployment(deploymentFilePath)
    verifyConfiguration(
      config,
      meta("baseUrl").toString,
      meta("esUrl").toString,
      meta("username").toString,
      meta("password").toString,
      "cloud-basic",
      "basic",
      deploymentVersion.version,
      deploymentVersion.value,
      buildHash,
      buildNumber,
      isSnapshotBuild = true,
      Option(meta("deploymentId").toString),
      meta("deleteDeploymentOnFinish").toString.toBoolean
    )
  }
}
