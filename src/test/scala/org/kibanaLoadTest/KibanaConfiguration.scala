package org.kibanaLoadTest

import com.typesafe.config.Config
import org.apache.http.HttpStatus
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.kibanaLoadTest.helpers.{Helper, Version}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{
  BooleanJsonFormat,
  LongJsonFormat,
  StringJsonFormat
}
import spray.json.lenses.JsonLenses._

class KibanaConfiguration {
  val logger = LoggerFactory.getLogger("Configuration")
  var baseUrl = ""
  var buildHash = ""
  var buildNumber = 0L
  var isSnapshotBuild = false
  var version = ""
  var buildVersion = ""
  var isSecurityEnabled = false
  var username = ""
  var password = ""
  var providerType = ""
  var providerName = ""
  var loginPayload = ""
  var loginStatusCode = 0
  // Elasticsearch
  var esUrl = ""
  var esVersion = ""
  var esBuildHash = ""
  var esBuildDate = ""
  var esLuceneVersion = ""
  // Cloud testing
  var deploymentId: Option[String] = None
  var deleteDeploymentOnFinish = true

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
    HttpClient.getKibanaStatus(kibanaHost) match {
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
        this.username = username
        this.password = password
        this.providerType = providerType
        this.providerName = providerName
        this.getElasticSearchInfo()
      case None =>
        throw new RuntimeException(
          "Failed to parse response with Kibana status"
        )
    }
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

    this.setLoginPayloadAndStatusCode(config)
    this.setDeploymentInfo(config)
    this.getElasticSearchInfo()
  }

  def setLoginPayloadAndStatusCode(config: Config): Unit = {
    val isAbove79x = new Version(this.buildVersion).isAbove79x
    if (
      isAbove79x && (!config.hasPathOrNull("auth.providerType") || !config
        .hasPathOrNull("auth.providerName"))
    ) {
      throw new RuntimeException(
        "Starting 7.10.0 Kibana authentication requires 'auth.providerType' " +
          "& 'auth.providerType' in payload, add it to your config file"
      )
    }

    this.loginPayload =
      if (isAbove79x) s"""{"providerType":"${config.getString(
        "auth.providerType"
      )}","providerName":"${config.getString(
        "auth.providerName"
      )}","currentURL":"${this.baseUrl}/login","params":{"username":"${this.username}","password":"${this.password}"}}"""
      else s"""{"username":"${this.username}","password":"${this.password}"}"""
    this.loginStatusCode = if (isAbove79x) 200 else 204
  }

  def setDeploymentInfo(config: Config): Unit = {
    this.deploymentId = if (config.hasPath("deploymentId")) {
      Option(config.getString("deploymentId"))
    } else None
    this.deleteDeploymentOnFinish =
      if (config.hasPath("deleteDeploymentOnFinish"))
        config.getBoolean("deleteDeploymentOnFinish")
      else true
  }

  def getElasticSearchInfo(): Unit = {
    HttpClient.getElasticSearchInfo(
      this.esUrl,
      this.username,
      this.password
    ) match {
      case Some(value) =>
        this.esVersion =
          value.extract[String](Symbol("version") / Symbol("number"))
        this.esBuildHash =
          value.extract[String](Symbol("version") / Symbol("build_hash"))
        this.esBuildDate = value
          .extract[String](Symbol("version") / Symbol("build_date"))
        this.esLuceneVersion =
          value.extract[String](Symbol("version") / Symbol("lucene_version"))
      case None =>
        throw new RuntimeException(
          "Failed to parse response with Elasticsearch status"
        )
    }
  }

  def print(): Unit = {
    logger.info(s"Base URL = ${this.baseUrl}")
    logger.info(s"Kibana version = ${this.buildVersion}")
    logger.info(s"Security Enabled = ${this.isSecurityEnabled}")
    logger.info(s"Auth payload = ${this.loginPayload}")
  }
}

object HttpClient {
  private val logger: Logger =
    LoggerFactory.getLogger("Configuration - HttpClient")

  def getKibanaStatus(kibanaHost: String): Option[String] = {
    val url = kibanaHost + "/api/status"
    logger.info(s"GET $url")
    val httpClient = HttpClientBuilder.create.build
    try {
      val request = new HttpGet(url)
      val response = httpClient.execute(request)
      response.getStatusLine.getStatusCode match {
        case HttpStatus.SC_OK =>
          EntityUtils.toString(response.getEntity, "UTF-8")
        case _ => return None
      }
    } catch {
      case _: Throwable =>
        logger.error("Exception occurred on getting Kibana status")
    } finally {
      httpClient.close()
    }

    None
  }

  def getElasticSearchInfo(
      esUrl: String,
      username: String,
      password: String
  ): Option[String] = {
    val url = esUrl
    logger.info(s"GET $url")
    var jsonString = ""
    val provider = new BasicCredentialsProvider
    provider.setCredentials(
      AuthScope.ANY,
      new UsernamePasswordCredentials(username, password)
    )
    val httpClient =
      HttpClientBuilder.create.setDefaultCredentialsProvider(provider).build
    try {
      val request = new HttpGet(url)
      val response = httpClient.execute(request)
      response.getStatusLine.getStatusCode match {
        case HttpStatus.SC_OK =>
          EntityUtils.toString(response.getEntity, "UTF-8")
        case _ => return None
      }
    } catch {
      case _: Throwable =>
        logger.error("Exception occurred on getting Elasticsearch status")
    } finally {
      httpClient.close()
    }
    None
  }
}
