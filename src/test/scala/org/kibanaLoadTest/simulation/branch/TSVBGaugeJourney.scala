package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Login, Visualize}
import org.kibanaLoadTest.simulation.BaseSimulation

class TSVBGaugeJourney extends BaseSimulation {
  val scenarioName = "GaugeJourney"
  props.maxUsers = 500

  val steps = exec(
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
        "tsvb",
        "b80e6540-b891-11e8-a6d9-e546fe2bba5f",
        "data/visualize/gauge_sold_per_day.json",
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
