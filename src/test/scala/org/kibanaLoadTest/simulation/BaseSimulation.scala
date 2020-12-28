package org.kibanaLoadTest.simulation

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{CloudHttpClient, Helper, HttpHelper, Version}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol.{IntJsonFormat, StringJsonFormat}
import spray.json.lenses.JsonLenses._

import java.io.File
import java.nio.file.Paths

class BaseSimulation extends Simulation {
  val logger: Logger = LoggerFactory.getLogger("Base Simulation")
  val CLOUD_DEPLOY_CONFIG = "config/deploy/default.conf"
  // "7.11.0-SNAPSHOT"
  val cloudDeployVersion: Option[String] = Option(System.getenv("cloudDeploy"))
  // "config/cloud-7.9.2.conf"
  val envConfig: String =
    Option(System.getenv("env")).getOrElse("config/local.conf")

  // appConfig is used to run load tests
  val appConfig: KibanaConfiguration = if (cloudDeployVersion.isDefined) {
    // create new deployment on Cloud
    createDeployment(cloudDeployVersion.get)
    // use existing deployment or local instance
  } else new KibanaConfiguration(Helper.readResourceConfigFile(envConfig))

  val lastDeploymentFilePath: String = Paths
    .get("target")
    .toAbsolutePath
    .normalize
    .toString + File.separator + "lastDeployment.txt"

  before {
    appConfig.print()

    // saving deployment info to target/lastDeployment.txt"
    if (appConfig.deploymentId.isDefined) {
      logger.info(s"Getting Kibana status info")
      val response = new HttpHelper(appConfig).getStatus
      if (response.length > 1) {
        val meta = Map(
          "deploymentId" -> appConfig.deploymentId.get,
          "baseUrl" -> appConfig.baseUrl,
          "version" -> appConfig.buildVersion,
          "buildHash" -> response
            .extract[String](Symbol("version") / Symbol("build_hash")),
          "buildNumber" -> response
            .extract[Int](Symbol("version") / Symbol("build_number"))
        )
        Helper.writeMapToFile(meta, lastDeploymentFilePath)
      }
    }

    // load sample data
    logger.info(s"Loading sample data")
    new HttpHelper(appConfig).addSampleData("ecommerce")

  }

  after {
    if (appConfig.deploymentId.isDefined) {
      // delete deployment
      new CloudHttpClient().deleteDeployment(appConfig.deploymentId.get)
    } else {
      // remove sample data
      try {
        logger.info(s"Removing sample data")
        new HttpHelper(appConfig).removeSampleData("ecommerce")
      } catch {
        case e: java.lang.RuntimeException =>
          println(s"Can't remove sample data\n ${e.printStackTrace()}")
      }
    }
  }

  def createDeployment(stackVersion: String): KibanaConfiguration = {
    val config = Helper.readResourceConfigFile(CLOUD_DEPLOY_CONFIG)
    val version = new Version(stackVersion)
    val providerName = if (version.isAbove79x) "cloud-basic" else "basic-cloud"
    val cloudClient = new CloudHttpClient
    val payload = cloudClient.preparePayload(stackVersion, config)
    val metadata = cloudClient.createDeployment(payload)
    cloudClient.waitForClusterToStart(metadata("deploymentId"))
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

  logger.info(s"Running ${getClass.getSimpleName} simulation")

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl(appConfig.baseUrl)
    .inferHtmlResources(
      BlackList(
        """.*\.js""",
        """.*\.css""",
        """.*\.gif""",
        """.*\.jpeg""",
        """.*\.jpg""",
        """.*\.ico""",
        """.*\.woff""",
        """.*\.woff2""",
        """.*\.(t|o)tf""",
        """.*\.png""",
        """.*detectportal\.firefox\.com.*"""
      ),
      WhiteList()
    )
    .acceptHeader(
      "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
    )
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
    .userAgentHeader(
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"
    )

  var defaultHeaders = Map(
    "Connection" -> "keep-alive",
    "kbn-version" -> appConfig.buildVersion,
    "Content-Type" -> "application/json",
    "Accept" -> "*/*",
    "Origin" -> appConfig.baseUrl,
    "Sec-Fetch-Site" -> "same-origin",
    "Sec-Fetch-Mode" -> "cors",
    "Sec-Fetch-Dest" -> "empty"
  )

  var defaultTextHeaders = Map("Content-Type" -> "text/html; charset=utf-8")

  if (appConfig.isSecurityEnabled) {
    defaultHeaders += ("Cookie" -> "${Cookie}")
    defaultTextHeaders += ("Cookie" -> "${Cookie}")
  }
}
