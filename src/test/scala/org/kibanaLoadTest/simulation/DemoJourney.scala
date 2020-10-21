package org.kibanaLoadTest.simulation

import java.io.File

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.getLastReportPath
import org.kibanaLoadTest.helpers.{ESWrapper, HttpHelper}
import org.kibanaLoadTest.scenario.{Canvas, Dashboard, Discover, Login}

import scala.concurrent.duration.DurationInt

class DemoJourney extends BaseSimulation {
  val scenarioName = s"Kibana demo journey ${appConfig.buildVersion} ${env}"

  val scn = scenario(scenarioName)
    .exec(Login.doLogin(appConfig.isSecurityEnabled, appConfig.loginPayload, appConfig.loginStatusCode).pause(5 seconds))
    .exec(Discover.doQuery(appConfig.baseUrl, defaultHeaders).pause(10 seconds))
    .exec(Dashboard.load(appConfig.baseUrl, defaultHeaders).pause(10 seconds))
    .exec(Canvas.loadWorkpad(appConfig.baseUrl, defaultHeaders))

  before {
    // load sample data
    new HttpHelper(appConfig)
      .loginIfNeeded()
      .addSampleData("ecommerce")
      .closeConnection()
  }

  after {
    // remove sample data
    try {
      new HttpHelper(appConfig)
        .loginIfNeeded()
        .removeSampleData("ecommerce")
        .closeConnection()
    } catch {
      case e: java.lang.RuntimeException => println(s"Can't remove sample data\n ${e.printStackTrace()}")
    }

    // ingest results to ES instance
    val ingest:Boolean = Option(System.getenv("ingest")).getOrElse("false").toBoolean
    if (ingest) {
      val logFilePath = getLastReportPath() + File.separator + "simulation.log"
      val esWrapper = new ESWrapper(appConfig)
      esWrapper.ingest(logFilePath, scenarioName)
    }
  }

  setUp(
    scn.inject(
      constantConcurrentUsers(20) during (3 minute), // 1
      rampConcurrentUsers(20) to (50) during (3 minute) // 2
    ).protocols(httpProtocol)
  ).maxDuration(15 minutes)

  // generate a closed workload injection profile
  // with levels of 10, 15, 20, 25 and 30 concurrent users
  // each level lasting 10 seconds
  // separated by linear ramps lasting 10 seconds
}