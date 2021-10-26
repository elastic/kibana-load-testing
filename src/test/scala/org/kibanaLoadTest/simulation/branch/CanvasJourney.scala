package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.Predef.{
  constantConcurrentUsers,
  rampConcurrentUsers,
  scenario
}
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Canvas, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class CanvasJourney extends BaseSimulation {
  val scenarioName = s"Canvas journey ${appConfig.buildVersion}"

  props.maxUsers = 800

  val scn: ScenarioBuilder = scenario(scenarioName)
    .exec(
      Login
        .doLogin(
          appConfig.isSecurityEnabled,
          appConfig.loginPayload,
          appConfig.loginStatusCode
        )
        .pause(5)
    )
    .exec(Canvas.loadWorkpad(appConfig.baseUrl, defaultHeaders))

  setUp(
    scn
      .inject(
        constantConcurrentUsers(20) during (1 * 60), // 1
        rampConcurrentUsers(20) to props.maxUsers during (3 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)
}
