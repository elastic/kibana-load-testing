package org.kibanaLoadTest.helpers

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.gatling.core.Predef.{BlackList, WhiteList, configuration}
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration

import java.io.File
import java.nio.file.Paths

object SimulationHelper {
  private val lastDeploymentFilePath: String = Paths
    .get("target")
    .toAbsolutePath
    .normalize
    .toString + File.separator + "lastDeployment.txt"

  def createDeployment(
      stackVersion: String,
      deployFile: String
  ): KibanaConfiguration = {
    val config =
      Helper.readResourceConfigFile(deployFile)
    val version = new Version(stackVersion)
    val providerName = if (version.isAbove79x) "cloud-basic" else "basic-cloud"
    val cloudClient = new CloudHttpClient
    val payload = cloudClient.preparePayload(stackVersion, config)
    val metadata = cloudClient.createDeployment(payload)
    val isReady = cloudClient.waitForClusterToStart(metadata("deploymentId"))
    // delete deployment if it was not finished successfully
    if (!isReady) {
      cloudClient.deleteDeployment(metadata("deploymentId"))
      throw new RuntimeException("Stop due to failed deployment...")
    }
    val hosts = cloudClient.getServiceUrl(
      metadata("deploymentId"),
      Array("apm", "kibana", "elasticsearch")
    )

    val lines = Array(
      "#!/usr/bin/env bash\n\n",
      s"export  KIBANA_URL=${hosts("kibana")}\n",
      s"export  ES_URL=${hosts("elasticsearch")}\n",
      s"export  USERNAME=${metadata("username")}\n",
      s"export  PASSWORD=${metadata("password")}\n"
    )

    // ./target/hosts.sh will be used to configure monitoring of loud instance
    Helper.writeFile("hosts.sh", lines)

    val cloudConfig = ConfigFactory
      .load()
      .withValue(
        "deploymentId",
        ConfigValueFactory.fromAnyRef(metadata("deploymentId"))
      )
      .withValue("app.host", ConfigValueFactory.fromAnyRef(hosts("kibana")))
      .withValue(
        "es.host",
        ConfigValueFactory.fromAnyRef(hosts("elasticsearch"))
      )
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

  def saveDeploymentMeta(config: KibanaConfiguration, users: Integer): Unit = {
    val meta = Map(
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
    Helper.writeMapToFile(meta, lastDeploymentFilePath)
  }
}
