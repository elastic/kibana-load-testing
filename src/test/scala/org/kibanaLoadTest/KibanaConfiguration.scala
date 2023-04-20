package org.kibanaLoadTest

import com.typesafe.config.Config
import org.kibanaLoadTest.helpers.{Helper, KbnClient}
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol.{
  BooleanJsonFormat,
  LongJsonFormat,
  StringJsonFormat
}
import spray.json.lenses.JsonLenses._

class KibanaConfiguration {
  val logger = LoggerFactory.getLogger("Configuration")
  var baseUrl: String = null
  var username: String = null
  var password: String = null
  var providerType: String = null
  var providerName: String = null
  // Elasticsearch
  var esUrl: String = null

  var version: String = null
  var buildVersion: String = null
  var isSnapshotBuild = false
  var buildHash: String = null
  var buildNumber = 0L
  var loginPayload: String = null
  // Cloud testing
  var deploymentId: Option[String] = None
  var deleteDeploymentOnFinish = false
  // http request header requires sid
  var setCookieHeader = false

  def this(
      kibanaHost: String,
      esHost: String,
      username: String,
      password: String,
      providerType: String,
      providerName: String
  ) = {
    this()
    this.baseUrl = Helper.validateUrl(
      kibanaHost,
      s"'kibanaHost' should be a valid Kibana URL"
    )
    this.esUrl = Helper.validateUrl(
      esHost,
      s"'esHost' should be a valid Elasticsearch URL"
    )
    this.username = username
    this.password = password
    this.providerType = providerType
    this.providerName = providerName
    this.loginPayload =
      s"""{"providerType":"$providerType","providerName":"$providerName","currentURL":"$baseUrl/login","params":{"username":"$username","password":"$password"}}"""
    this.readKibanaBuildInfo()
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
    this.baseUrl = Helper.validateUrl(
      config.getString("host.kibana"),
      s"'host.kibana' should be a valid Kibana URL"
    )
    this.esUrl = Helper.validateUrl(
      config.getString("host.es"),
      s"'host.es' should be a valid ES URL"
    )
    this.username = config.getString("auth.username")
    this.password = config.getString("auth.password")
    this.providerType = config.getString("auth.providerType")
    this.providerName = config.getString("auth.providerName")
    if (this.providerType == null || this.providerName == null) {
      throw new RuntimeException(
        "Starting 7.10.0 Kibana authentication requires 'auth.providerType' " +
          "& 'auth.providerType' in payload, add it to your config file"
      )
    }
    this.readKibanaBuildInfo()
    this.loginPayload =
      s"""{"providerType":"$providerType","providerName":"$providerName","currentURL":"$baseUrl/login","params":{"username":"$username","password":"$password"}}"""
    this.setDeploymentInfo(config)
  }

  def setDeploymentInfo(config: Config): Unit = {
    this.deploymentId = if (config.hasPath("deploymentId")) {
      Option(config.getString("deploymentId"))
    } else None
    this.deleteDeploymentOnFinish =
      if (config.hasPath("deleteDeploymentOnFinish"))
        config.getBoolean("deleteDeploymentOnFinish")
      else false
  }

  def readKibanaBuildInfo(): Unit = {
    val client = new KbnClient(
      this.baseUrl,
      this.username,
      this.password,
      this.providerName,
      this.providerType
    )
    Option(client.getKibanaStatusInfo()) match {
      case Some(value) =>
        this.buildHash =
          value.extract[String](Symbol("version") / Symbol("build_hash"))
        this.buildNumber =
          value.extract[Long](Symbol("version") / Symbol("build_number"))
        this.isSnapshotBuild = value
          .extract[Boolean](Symbol("version") / Symbol("build_snapshot"))
        this.version =
          value.extract[String](Symbol("version") / Symbol("number"))
        this.buildVersion =
          if (this.isSnapshotBuild) s"${this.version}-SNAPSHOT"
          else this.version
      case None =>
        logger.error(
          "!!! Make sure Kibana is up & running before simulation start!!!"
        )
        throw new RuntimeException(
          "Failed to parse response with Kibana status"
        )
    }
  }

  def print(): Unit = {
    logger.info(s"Kibana baseUrl = ${this.baseUrl}")
    logger.info(s"Kibana version = ${this.buildVersion}")
  }
}
