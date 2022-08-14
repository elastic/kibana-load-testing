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
  var esUrl = ""
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

  // ES data
  var esVersion = ""
  var esBuildHash = ""
  var esBuildDate = ""
  var esLuceneVersion = ""

  def this(
      kibanaHost: String,
      kibanaVersion: String,
      esHost: String,
      username: String,
      password: String,
      providerType: String,
      providerName: String
  ) = {
    this()
    // read required values
    this.baseUrl = Helper.validateUrl(
      kibanaHost,
      s"'kibanaHost' should be a valid Kibana URL"
    )
    this.esUrl =
      Helper.validateUrl(esHost, s"'esHost' should be a valid ES URL")
    this.buildVersion = kibanaVersion
    this.version = new Version(this.buildVersion).version
    this.isSecurityEnabled = true
    this.username = username
    this.password = password
    this.isAbove79x = new Version(this.buildVersion).isAbove79x

    this.loginPayload =
      if (this.isAbove79x)
        s"""{"providerType":"$providerType","providerName":"$providerName","currentURL":"${this.baseUrl}/login","params":{"username":"${this.username}","password":"${this.password}"}}"""
      else s"""{"username":"${this.username}","password":"${this.password}"}"""
    this.loginStatusCode = if (this.isAbove79x) 200 else 204
    this.deploymentId = None
    this.deleteDeploymentOnFinish = false
  }

  def this(config: Config) = {
    this()
    // validate config
    if (
      !config.hasPathOrNull("host.kibana")
      || !config.hasPathOrNull("host.es")
      || !config.hasPathOrNull("host.version")
      || !config.hasPathOrNull("security.on")
      || !config.hasPathOrNull("auth.username")
      || !config.hasPathOrNull("auth.password")
    ) {
      throw new RuntimeException(
        "Incorrect configuration - required values:\n" +
          "'host.kibana' should be a valid Kibana host with protocol & port, e.g. 'http://localhost:5620'\n" +
          "'host.es' should be a valid ElasticSearch host with protocol & port, e.g. 'http://localhost:9220'\n" +
          "'app.version' should be a Stack version, e.g. '7.8.1'\n" +
          "'security.on' should be false for OSS, otherwise true\n" +
          "'auth.username' and 'auth.password' should be valid credentials"
      )
    }
    // read required values
    this.baseUrl = Helper.validateUrl(
      config.getString("host.kibana"),
      s"'host.kibana' should be a valid Kibana URL"
    )
    this.esUrl = Helper.validateUrl(
      config.getString("host.es"),
      s"'host.es' should be a valid ES URL"
    )
    this.buildVersion = config.getString("host.version")
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
    val httpClient = new HttpHelper(this)
    val response = httpClient.getStatus

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

    if (!this.buildVersion.startsWith(configBuildVersion)) {
      logger.error(
        "Kibana version mismatch: instance " + this.buildVersion + " vs config " + configBuildVersion
      )
    }

    val esMetaJson = httpClient.getElasticSearchData
    val version = esMetaJson.hcursor.downField("version")
    this.esVersion = version.get[String]("number").getOrElse(null)
    this.esBuildHash = version.get[String]("build_hash").getOrElse(null)
    this.esBuildDate = version.get[String]("build_date").getOrElse(null)
    this.esLuceneVersion = version.get[String]("lucene_version").getOrElse(null)

    this
  }

  def print(): Unit = {
    logger.info(s"Base URL = ${this.baseUrl}")
    logger.info(s"Kibana version = ${this.buildVersion}")
    logger.info(s"Security Enabled = ${this.isSecurityEnabled}")
    logger.info(s"Auth payload = ${this.loginPayload}")
  }
}
