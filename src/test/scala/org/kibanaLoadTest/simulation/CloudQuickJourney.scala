package org.kibanaLoadTest.simulation

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Canvas, Dashboard, Discover, Login}

class CloudQuickJourney extends BaseSimulation {
  val scenarioName = s"Kibana cloud quick journey ${appConfig.buildVersion}"

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
    .exec(Discover.doQuery(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Dashboard.load(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Canvas.loadWorkpad(appConfig.baseUrl, defaultHeaders))

  setUp(
    scn
      .inject(
        atOnceUsers(80) // all virtual users start scenario at once
      )
      .protocols(httpProtocol)
  ).maxDuration(10 * 60)
}
