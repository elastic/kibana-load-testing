package org.kibanaLoadTest.helpers

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.{getCIMeta, getTargetPath}
import org.slf4j.{Logger, LoggerFactory}
import java.io.File
import java.nio.file.{Files, Paths, StandardCopyOption}

object SimulationHelper {

  val logger: Logger = LoggerFactory.getLogger("SimulationHelper")
  val LATEST_AVAILABLE = ".x"

  private val lastRunFilePath: String = Paths
    .get("target")
    .toAbsolutePath
    .normalize
    .toString + File.separator + "lastRun.txt"

  def createDeployment(
      stackVersion: String,
      deployFile: String
  ): KibanaConfiguration = {
    val config =
      Helper.readResourceConfigFile(deployFile)
    val cloudClient = new CloudHttpClient
    val version = if (stackVersion.endsWith(LATEST_AVAILABLE)) {
      cloudClient.getLatestAvailableVersion(
        stackVersion.replace(LATEST_AVAILABLE, ""),
        config.getString("category")
      )
    } else new Version(stackVersion)
    val providerName = if (version.isAbove79x) "cloud-basic" else "basic-cloud"
    val payload = cloudClient.preparePayload(stackVersion, config)
    val metadata = cloudClient.createDeployment(payload)
    val isReady = cloudClient.waitForClusterToStart(metadata("deploymentId"))
    // delete deployment if it was not finished successfully
    if (!isReady) {
      cloudClient.deleteDeployment(metadata("deploymentId"))
      throw new RuntimeException("Stop due to failed deployment...")
    }
    val host = cloudClient.getKibanaUrl(metadata("deploymentId"))
    val cloudConfig = ConfigFactory
      .load()
      .withValue(
        "deploymentId",
        ConfigValueFactory.fromAnyRef(metadata("deploymentId"))
      )
      .withValue("app.host", ConfigValueFactory.fromAnyRef(host))
      .withValue(
        "app.version",
        ConfigValueFactory.fromAnyRef(version.get)
      )
      .withValue("security.on", ConfigValueFactory.fromAnyRef(true))
      .withValue("auth.providerType", ConfigValueFactory.fromAnyRef("basic"))
      .withValue(
        "auth.providerName",
        ConfigValueFactory.fromAnyRef(providerName)
      )
      .withValue(
        "auth.username",
        ConfigValueFactory.fromAnyRef(metadata("username"))
      )
      .withValue(
        "auth.password",
        ConfigValueFactory.fromAnyRef(metadata("password"))
      )

    new KibanaConfiguration(cloudConfig)
  }

  def useExistingDeployment(id: String): KibanaConfiguration = {
    val cloudDeploymentFilePath: String = Paths
      .get("target")
      .toAbsolutePath
      .normalize
      .toString + File.separator + "cloudDeployment.txt"
    val meta = Helper.readFileToMap(cloudDeploymentFilePath)
    val version = new Version(meta("version").toString)
    val providerName = if (version.isAbove79x) "cloud-basic" else "basic-cloud"
    val cloudConfig = ConfigFactory
      .load()
      .withValue(
        "deploymentId",
        ConfigValueFactory.fromAnyRef(id)
      )
      .withValue(
        "deleteDeploymentOnFinish",
        ConfigValueFactory.fromAnyRef(meta("deleteDeploymentOnFinish"))
      )
      .withValue("app.host", ConfigValueFactory.fromAnyRef(meta("host")))
      .withValue(
        "app.version",
        ConfigValueFactory.fromAnyRef(version.get)
      )
      .withValue("security.on", ConfigValueFactory.fromAnyRef(true))
      .withValue("auth.providerType", ConfigValueFactory.fromAnyRef("basic"))
      .withValue(
        "auth.providerName",
        ConfigValueFactory.fromAnyRef(providerName)
      )
      .withValue(
        "auth.username",
        ConfigValueFactory.fromAnyRef(meta("username"))
      )
      .withValue(
        "auth.password",
        ConfigValueFactory.fromAnyRef(meta("password"))
      )
    new KibanaConfiguration(cloudConfig)
  }

  def saveRunConfiguration(
      config: KibanaConfiguration,
      users: Integer
  ): Unit = {
    val deployMeta = Map(
      "deploymentId" -> (if (config.deploymentId.isDefined)
                           config.deploymentId.get
                         else ""),
      "isCloudDeployment" -> config.deploymentId.isDefined,
      "baseUrl" -> config.baseUrl,
      "buildHash" -> config.buildHash,
      "buildNumber" -> config.buildNumber,
      "version" -> config.version,
      "isSnapshotBuild" -> config.isSnapshotBuild,
      "maxUsers" -> users
    )
    val meta = deployMeta.++(getCIMeta)
    Helper.writeMapToFile(meta, lastRunFilePath)
  }

  def randomWait(): Unit = {
    val secToWait = Helper.getRandomNumber(5, 60) + Helper.getRandomNumber(
      5,
      60
    ) // between 10 and 120 sec
    logger.info(s"Delay on start: $secToWait seconds")
    Thread.sleep(secToWait * 1000)
  }

  def copyRunConfigurationToReportPath(): Unit = {
    val currentPath =
      getTargetPath + File.separator + "lastRun.txt"
    val copyPath = Helper.getLastReportPath + File.separator + "testRun.txt"
    Files.copy(
      Paths.get(currentPath),
      Paths.get(copyPath),
      StandardCopyOption.REPLACE_EXISTING
    )
  }
}
