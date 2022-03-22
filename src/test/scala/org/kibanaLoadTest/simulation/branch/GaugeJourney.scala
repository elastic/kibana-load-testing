package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.moveResponseLogToResultsDir
import org.kibanaLoadTest.scenario.{Login, Visualize}
import org.kibanaLoadTest.helpers.{Helper, HttpHelper, SimulationHelper}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.lenses.JsonLenses.element
import spray.json.lenses.JsonLenses._
import spray.json.DefaultJsonProtocol._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class GaugeJourney extends Simulation {
  val logger: Logger = LoggerFactory.getLogger("GaugeJourney Simulation")
  val scenarioName = "GaugeJourney"

  object props {
    var maxUsers = 500
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

  val envConfig: String = System.getProperty("env", "config/local.conf")
  val appConfig: KibanaConfiguration = new KibanaConfiguration(
    Helper.readResourceConfigFile(envConfig)
  ).syncWithInstance()

  val httpHelper = new HttpHelper(appConfig)
  var httpProtocol: HttpProtocolBuilder = httpHelper.getProtocol
  httpProtocol.baseUrl(appConfig.baseUrl)
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

  after {
    SimulationHelper.copyRunConfigurationToReportPath()
    // move response log from /target to gatling report folder
    moveResponseLogToResultsDir()
    try {
      httpHelper.removeSampleData("ecommerce")
    } catch {
      case e: java.lang.RuntimeException =>
        println(s"Can't remove sample data\n ${e.printStackTrace()}")
    }
  }

  logger.info(
    s"Running ${getClass.getSimpleName} simulation with ${props.maxUsers} users"
  )
  appConfig.print()
  // saving deployment info to target/lastRun.txt"
  SimulationHelper.saveRunConfiguration(appConfig, props.maxUsers)
  // load sample data
  httpHelper.addSampleData("ecommerce")
  // wait 30s for data ingestion to be completed
  Thread.sleep(25 * 1000)
  val soPath: String =
    getClass.getResource("/saved_objects/gauge_viz.ndjson").getPath
  val importResponse: String =
    new HttpHelper(appConfig).importSavedObjects(soPath)
  val vizId: String =
    importResponse.extract[String](
      Symbol("successResults") / element(0) / Symbol("destinationId")
    )
  Thread.sleep(5 * 1000)

  val steps: ChainBuilder = exec(
    Login
      .doLogin(
        appConfig.isSecurityEnabled,
        appConfig.loginPayload,
        appConfig.loginStatusCode
      )
      .pause(5)
  ).exec(
    Visualize
      .load(
        "gauge",
        vizId,
        "data/visualize/gatling_gauge.json",
        appConfig.baseUrl,
        defaultHeaders
      )
      .pause(5)
  )

  val warmupScn: ScenarioBuilder = scenario("warmup").exec(steps)
  val scn: ScenarioBuilder = scenario(scenarioName).exec(steps)

  setUp(
    warmupScn
      .inject(
        constantConcurrentUsers(20) during (1 * 30),
        rampConcurrentUsers(20) to props.maxUsers during (2 * 60)
      )
      .protocols(httpProtocol)
      .andThen(
        scn
          .inject(
            constantConcurrentUsers(props.maxUsers) during (4 * 60)
          )
          .protocols(httpProtocol)
      )
  ).maxDuration(props.simulationTimeout * 2)
}
