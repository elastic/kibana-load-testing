package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Visualize, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class TSVBTimeSeriesJourney extends BaseSimulation {
  val scenarioName = s"Demo journey ${appConfig.buildVersion}"

  props.maxUsers = 1200

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

  setUp(
    scn
      .inject(
        constantConcurrentUsers(20) during (3 * 60), // 1
        rampConcurrentUsers(20) to props.maxUsers during (3 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)
}
