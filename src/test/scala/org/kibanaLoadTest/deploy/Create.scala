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

  def main(args: Array[String]): Unit = {
    val deployConfig: String =
      System.getProperty("deploymentConfig", "config/deploy/default.conf")
    val versionString: String = System.getProperty("cloudStackVersion")
    // validate version
    val version = new Version(versionString)

    val cloudClient = new CloudHttpClient
    val payload = cloudClient.preparePayload(
      version.get,
      Helper.readResourceConfigFile(deployConfig)
    )
    var metadata = cloudClient.createDeployment(payload)
    val isReady = cloudClient.waitForClusterToStart(metadata("deploymentId"))
    // delete deployment if it was not finished successfully
    if (!isReady) {
      cloudClient.deleteDeployment(metadata("deploymentId"))
      throw new RuntimeException("Stop due to failed deployment...")
    }

    val host = cloudClient.getKibanaUrl(metadata("deploymentId"))
    metadata += "version" -> version.get
    metadata += "host" -> host
    // do not delete deployment after simulation run
    metadata += "deleteDeploymentOnFinish" -> "false"

    Helper.writeMapToFile(metadata, cloudDeploymentFilePath)
  }
}
