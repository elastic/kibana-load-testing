package org.kibanaLoadTest.simulation

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Canvas, Dashboard, Discover, Login}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class CloudAtOnceJourney extends BaseSimulation {
  def scenarioName(module: String): String = {
    s"Cloud  atOnce ${module} ${appConfig.buildVersion}"
  }

  val simulationTimeout =
    FiniteDuration(
      10,
      TimeUnit.MINUTES
    )

  val loginPause =
    FiniteDuration(
      2,
      TimeUnit.SECONDS
    )

  val scnPause =
    FiniteDuration(
      20,
      TimeUnit.SECONDS
    )

  val scnDiscover: ScenarioBuilder = scenario(scenarioName("discover"))
    .exec(loginStep.pause(loginPause))
    .exec(Discover.doQuery(appConfig.baseUrl, defaultHeaders))

  val scnDashboard: ScenarioBuilder = scenario(scenarioName("dashboard"))
    .exec(loginStep.pause(loginPause))
    .exec(Dashboard.load(appConfig.baseUrl, defaultHeaders))

  val scnCanvas: ScenarioBuilder = scenario(scenarioName("canvas"))
    .exec(loginStep.pause(loginPause))
    .exec(Canvas.loadWorkpad(appConfig.baseUrl, defaultHeaders))

  setUp(
    scnDiscover
      .inject(atOnceUsers(80), nothingFor(scnPause))
      .andThen(
        scnDashboard
          .inject(atOnceUsers(80), nothingFor(scnPause))
          .andThen(scnCanvas.inject(atOnceUsers(80)))
      )
  ).protocols(httpProtocol).maxDuration(simulationTimeout)

}
