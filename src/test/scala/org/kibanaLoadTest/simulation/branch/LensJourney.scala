package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{http, status}
import org.kibanaLoadTest.scenario.{Lens, Login}
import org.kibanaLoadTest.simulation.BaseSimulation
import io.gatling.http.Predef._

class LensJourney extends BaseSimulation {
  val scenarioName = s"Branch lens journey ${appConfig.buildVersion}"

  props.maxUsers = 200

  val steps = exec(
    Login
      .doLogin(
        appConfig.isSecurityEnabled,
        appConfig.loginPayload,
        appConfig.loginStatusCode
      )
      .pause(5)
  ).exec(
    Lens
      .load(
        "c762b7a0-f5ea-11eb-a78e-83aac3c38a60",
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
            constantConcurrentUsers(props.maxUsers) during (3 * 60)
          )
          .protocols(httpProtocol)
      )
  ).maxDuration(props.simulationTimeout * 2)
}
