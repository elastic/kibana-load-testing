package org.kibanaLoadTest

import com.typesafe.config._
import org.kibanaLoadTest.helpers.{Helper, HttpHelper, Version}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{
  BooleanJsonFormat,
  LongJsonFormat,
  StringJsonFormat
}
import spray.json.lenses.JsonLenses._

class KibanaConfiguration {

  val logger: Logger = LoggerFactory.getLogger("KibanaConfiguration")
  var baseUrl = ""
  var version = ""
  var buildVersion = ""
  var isSecurityEnabled = false
  var username = ""
  var password = ""
  var loginPayload = ""
  var loginStatusCode = 200
  var isAbove79x = true
  var deploymentId: Option[String] = None
  var buildHash = ""
  var buildNumber: Long = 0
  var isSnapshotBuild = false
  var deleteDeploymentOnFinish = true

  def this(config: Config) = {
    this()
    // validate config
    if (
      !config.hasPathOrNull("app.host")
      || !config.hasPathOrNull("app.version")
      || !config.hasPathOrNull("security.on")
      || !config.hasPathOrNull("auth.username")
      || !config.hasPathOrNull("auth.password")
    ) {
      throw new RuntimeException(
        "Incorrect configuration - required values:\n" +
          "'app.host' should be a valid Kibana host with protocol & port, e.g. 'http://localhost:5620'\n" +
          "'app.version' should be a Stack version, e.g. '7.8.1'\n" +
          "'security.on' should be false for OSS, otherwise true\n" +
          "'auth.username' and 'auth.password' should be valid credentials"
      )
    }
    // read required values
    this.baseUrl = Helper.validateUrl(
      config.getString("app.host"),
      s"'app.host' should be a valid Kibana URL"
    )
    this.buildVersion = config.getString("app.version")
    this.version = new Version(this.buildVersion).version
    this.isSecurityEnabled = config.getBoolean("security.on")
    this.username = config.getString("auth.username")
    this.password = config.getString("auth.password")
    this.isAbove79x = new Version(this.buildVersion).isAbove79x

    if (
      this.isAbove79x && (!config.hasPathOrNull("auth.providerType") || !config
        .hasPathOrNull("auth.providerName"))
    ) {
      throw new RuntimeException(
        "Starting 7.10.0 Kibana authentication requires 'auth.providerType' " +
          "& 'auth.providerType' in payload, add it to your config file"
      )
    }

    this.loginPayload =
      if (this.isAbove79x) s"""{"providerType":"${config.getString(
        "auth.providerType"
      )}","providerName":"${config.getString(
        "auth.providerName"
      )}","currentURL":"${this.baseUrl}/login","params":{"username":"${this.username}","password":"${this.password}"}}"""
      else s"""{"username":"${this.username}","password":"${this.password}"}"""
    this.loginStatusCode = if (this.isAbove79x) 200 else 204
    this.deploymentId = if (config.hasPath("deploymentId")) {
      Option(config.getString("deploymentId"))
    } else None
    this.deleteDeploymentOnFinish =
      if (config.hasPath("deleteDeploymentOnFinish"))
        config.getBoolean("deleteDeploymentOnFinish")
      else true
  }

  def syncWithInstance(): KibanaConfiguration = {
    logger.info(s"Getting Kibana status info")
    val response = new HttpHelper(this).getStatus
    this.buildHash =
      response.extract[String](Symbol("version") / Symbol("build_hash"))
    this.buildNumber =
      response.extract[Long](Symbol("version") / Symbol("build_number"))
    this.isSnapshotBuild = response
      .extract[Boolean](Symbol("version") / Symbol("build_snapshot"))
    this.version =
      response.extract[String](Symbol("version") / Symbol("number"))

    val configBuildVersion = this.buildVersion
    this.buildVersion =
      if (this.isSnapshotBuild) s"${this.version}-SNAPSHOT"
      else this.version

    if (!this.buildVersion.startsWith(configBuildVersion))
      throw new RuntimeException(
        "Kibana version mismatch: instance " + this.buildVersion + " vs config " + configBuildVersion
      )

    this
  }

  def print(): Unit = {
    logger.info(s"Base URL = ${this.baseUrl}")
    logger.info(s"Kibana version = ${this.buildVersion}")
    logger.info(s"Security Enabled = ${this.isSecurityEnabled}")
    logger.info(s"Auth payload = ${this.loginPayload}")
  }
}
