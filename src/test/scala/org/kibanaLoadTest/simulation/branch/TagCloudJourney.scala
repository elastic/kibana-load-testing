package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Visualize, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class TagCloudJourney extends BaseSimulation {
  val scenarioName = s"Demo journey ${appConfig.buildVersion}"

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
    .exec(
      Visualize
        .load(
          "tag_cloud",
          "b72dd430-bb4d-11e8-9c84-77068524bcab",
          "data/visualize/tag_cloud_top_products.json",
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
