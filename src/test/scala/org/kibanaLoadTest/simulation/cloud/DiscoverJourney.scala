package org.kibanaLoadTest.simulation.cloud

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Discover, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class DiscoverJourney extends BaseSimulation {
  val scenarioName = s"Cloud discover journey ${appConfig.buildVersion}"

  props.maxUsers = 1000

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
        constantUsersPerSec(10) during (30), // 1
        rampUsersPerSec(10) to props.maxUsers during (6 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)
}
