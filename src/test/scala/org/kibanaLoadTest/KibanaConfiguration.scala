package org.kibanaLoadTest

import com.typesafe.config._
import org.kibanaLoadTest.helpers.{Helper, HttpHelper, Version}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{
  IntJsonFormat,
  StringJsonFormat,
  BooleanJsonFormat
}
import spray.json.lenses.JsonLenses._

class KibanaConfiguration {

  val logger: Logger = LoggerFactory.getLogger("KibanaConfiguration")
  var baseUrl = ""
  var buildVersion = ""
  var isSecurityEnabled = false
  var username = ""
  var password = ""
  var loginPayload = ""
  var loginStatusCode = 200
  var isAbove79x = true
  var deploymentId: Option[String] = None
  var buildHash = ""
  var buildNumber = 0
  var isSnapshotBuild = false

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

    logger.info(s"Getting Kibana status info")
    val response = new HttpHelper(this).getStatus
    this.buildHash =
      response.extract[String](Symbol("version") / Symbol("build_hash"))
    this.buildNumber =
      response.extract[Int](Symbol("version") / Symbol("build_number"))
    this.isSnapshotBuild = response
      .extract[Boolean](Symbol("version") / Symbol("build_snapshot"))

    this.buildVersion =
      if (this.isSnapshotBuild) s"${this.buildNumber}-SNAPSHOT"
      else this.buildNumber.toString
    if (!this.buildVersion.startsWith(config.getString("app.version")))
      throw new RuntimeException(
        s"Kibana version mismatch: instance ${this.buildVersion} vs config ${config.getString("app.version")}"
      )
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
    } else Option(System.getenv("DEPLOYMENT_ID"))
  }

  def print(): Unit = {
    logger.info(s"Base URL = ${this.baseUrl}")
    logger.info(s"Kibana version = ${this.buildVersion}")
    logger.info(s"Security Enabled = ${this.isSecurityEnabled}")
    logger.info(s"Auth payload = ${this.loginPayload}")
  }
}
