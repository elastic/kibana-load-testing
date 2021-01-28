package org.kibanaLoadTest.simulation.local

import org.kibanaLoadTest.simulation.BaseSimulation
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Canvas, Dashboard, Discover}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class AllAtOnceJourney extends BaseSimulation {
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
      .inject(atOnceUsers(200), nothingFor(scnPause))
      .andThen(
        scnDashboard
          .inject(atOnceUsers(200), nothingFor(scnPause))
          .andThen(scnCanvas.inject(atOnceUsers(200)))
      )
  ).protocols(httpProtocol).maxDuration(simulationTimeout)
}
