package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Lens, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class LensJourney extends BaseSimulation {
  val scenarioName = s"Lens journey ${appConfig.buildVersion}"

  props.maxUsers = 1500

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
    .exec(
      Lens
        .load(
          "c762b7a0-f5ea-11eb-a78e-83aac3c38a60",
          appConfig.baseUrl,
          defaultHeaders
        )
        .pause(5)
    )

  setUp(
    scn
      .inject(
        constantConcurrentUsers(20) during (1 * 60), // 1
        rampConcurrentUsers(20) to props.maxUsers during (3 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)
}
