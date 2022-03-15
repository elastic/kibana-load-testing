package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Visualize, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class TSVBTimeSeriesJourney extends BaseSimulation {
  val scenarioName = s"TimeSeriesJourney"
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
        "45e07720-b890-11e8-a6d9-e546fe2bba5f",
        "data/visualize/time_series_promotion_tracking.json",
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
