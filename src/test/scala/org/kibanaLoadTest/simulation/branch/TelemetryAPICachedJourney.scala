package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Login, Home, TelemetryAPI}
import org.kibanaLoadTest.simulation.BaseSimulation

class TelemetryAPICachedJourney extends BaseSimulation {
  val scenarioName = s"Branch cached telemetry journey ${appConfig.buildVersion}"

  props.maxUsers = 400

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
    .exec(TelemetryAPI.cached(appConfig.baseUrl, defaultHeaders).pause(1))
    

  setUp(
    scn
      .inject(
        constantConcurrentUsers(20) during (3 * 60), // 1
        rampConcurrentUsers(20) to props.maxUsers during (3 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)
}
