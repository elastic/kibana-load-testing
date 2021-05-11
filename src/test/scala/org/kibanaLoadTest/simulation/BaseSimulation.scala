package org.kibanaLoadTest.simulation

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{
  CloudHttpClient,
  Helper,
  HttpHelper,
  SimulationHelper
}
import org.kibanaLoadTest.scenario.Login
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class BaseSimulation extends Simulation {
  val logger: Logger = LoggerFactory.getLogger("Base Simulation")

  object props {
    var maxUsers = 50
    var simulationTimeout: FiniteDuration =
      FiniteDuration(
        10,
        TimeUnit.MINUTES
      )
    var loginPause: FiniteDuration =
      FiniteDuration(
        2,
        TimeUnit.SECONDS
      )
    var scnPause: FiniteDuration =
      FiniteDuration(
        20,
        TimeUnit.SECONDS
      )
  }

  // -DdeploymentId=67d0195ac345578bcab6e561ff5, optional to use existing deployment
  val deploymentId: Option[String] = Option(
    System.getProperty("deploymentId")
  )
  // -DdeploymentConfig=path/to/config, default one deploys basic instance on GCP
  val CLOUD_DEPLOY_CONFIG: String =
    System.getProperty("deploymentConfig", "config/deploy/default.conf")
  // -DcloudDeployVersion=8.0.0-SNAPSHOT, optional to deploy Cloud instance
  val cloudDeployVersion: Option[String] = Option(
    System.getProperty("cloudStackVersion")
  )
  // -DenvConfig=path/to/config, default is a branch instance
  val envConfig: String = System.getProperty("env", "config/local.conf")
  // appConfig is used to run load tests

  val appConfig: KibanaConfiguration = if (deploymentId.isDefined) {
    logger.info(s"Using existing deployment: ${deploymentId.get}")
    SimulationHelper
      .useExistingDeployment(deploymentId.get)
      .syncWithInstance()
  } else if (cloudDeployVersion.isDefined) {
    // create new deployment on Cloud
    logger.info(s"Reading deployment configuration: $CLOUD_DEPLOY_CONFIG")
    SimulationHelper
      .createDeployment(
        stackVersion = cloudDeployVersion.get,
        deployFile = CLOUD_DEPLOY_CONFIG
      )
      .syncWithInstance()
    // use existing deployment or branch instance
  } else
    new KibanaConfiguration(Helper.readResourceConfigFile(envConfig))
      .syncWithInstance()

  val httpHelper = new HttpHelper(appConfig)
  var httpProtocol: HttpProtocolBuilder = httpHelper.getProtocol
  var defaultHeaders: Map[String, String] = httpHelper.getDefaultHeaders
  var defaultTextHeaders: Map[String, String] = httpHelper.defaultTextHeaders

  if (appConfig.isSecurityEnabled) {
    defaultHeaders += ("Cookie" -> "${Cookie}")
    defaultTextHeaders += ("Cookie" -> "${Cookie}")
  }

  var loginStep: ChainBuilder = Login
    .doLogin(
      appConfig.isSecurityEnabled,
      appConfig.loginPayload,
      appConfig.loginStatusCode
    )

  before {
    logger.info(
      s"Running ${getClass.getSimpleName} simulation with ${props.maxUsers} users"
    )
    appConfig.print()
    // saving deployment info to target/lastRun.txt"
    SimulationHelper.saveRunConfiguration(appConfig, props.maxUsers)
    // load sample data
    httpHelper.addSampleData("ecommerce")
    // wait 30s for data ingestion to be completed
    Thread.sleep(30 * 1000)
  }

  after {
    SimulationHelper.copyRunConfigurationToReportPath()
    if (
      appConfig.deploymentId.isDefined && appConfig.deleteDeploymentOnFinish
    ) {
      // delete deployment
      new CloudHttpClient().deleteDeployment(appConfig.deploymentId.get)
    } else {
      // remove sample data
      try {
        httpHelper.removeSampleData("ecommerce")
      } catch {
        case e: java.lang.RuntimeException =>
          println(s"Can't remove sample data\n ${e.printStackTrace()}")
      }
    }
  }
}
