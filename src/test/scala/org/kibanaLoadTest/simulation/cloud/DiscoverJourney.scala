package org.kibanaLoadTest.simulation.cloud

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Discover, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class DiscoverJourney extends BaseSimulation {
  val scenarioName = s"Cloud discover journey ${appConfig.buildVersion}"

  props.maxUsers = 300

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
    .exec(Discover.load(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Discover.do2ExtraQueries(appConfig.baseUrl, defaultHeaders).pause(10))

  setUp(
    scn
      .inject(
        constantConcurrentUsers(20) during (1 * 60), // 1
        rampConcurrentUsers(20) to props.maxUsers during (3 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)
}
