package org.kibanaLoadTest.deploy

import org.kibanaLoadTest.helpers.{CloudHttpClient, Helper, Version}
import java.io.File
import java.nio.file.Paths

object Create {
  private val cloudDeploymentFilePath: String = Paths
    .get("target")
    .toAbsolutePath
    .normalize
    .toString + File.separator + "cloudDeployment.txt"
  private val LATEST_AVAILABLE = ".x"

  def main(args: Array[String]): Unit = {
    val deployConfigPath: String =
      System.getProperty("deploymentConfig", "config/deploy/default.conf")
    val deployConfig = Helper.readResourceConfigFile(deployConfigPath)
    val versionString: String = System.getProperty("cloudStackVersion")

    val cloudClient = new CloudHttpClient
    val version = if (versionString.endsWith(LATEST_AVAILABLE)) {
      cloudClient.getLatestAvailableVersion(
        versionString.replace(LATEST_AVAILABLE, ""),
        deployConfig.getString("category")
      )
    } else new Version(versionString)

    val payload = cloudClient.preparePayload(
      version.get,
      deployConfig
    )
    val deployment = cloudClient.createDeployment(payload)
    val isReady = cloudClient.waitForClusterToStart(deployment)
    // delete deployment if it was not finished successfully
    if (!isReady) {
      cloudClient.deleteDeployment(deployment.id)
      throw new RuntimeException("Stop due to failed deployment...")
    }

    val hosts = cloudClient.getPublicUrls(deployment.id)

    val metadata = Map(
      "deploymentId" -> deployment.id,
      "username" -> deployment.username,
      "password" -> deployment.password,
      "version" -> version.get,
      "baseUrl" -> hosts.get("kibana").get,
      "esUrl" -> hosts.get("es").get,
      // do not delete deployment after simulation run
      "deleteDeploymentOnFinish" -> "false"
    )

    Helper.writeMapToFile(metadata, cloudDeploymentFilePath)
  }
}
