package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Canvas, Dashboard, Discover, Login}
import org.kibanaLoadTest.simulation.BaseSimulation

class DemoJourney extends BaseSimulation {
  val scenarioName = s"Demo journey ${appConfig.buildVersion}"

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
    .exec(Discover.load(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Discover.do2ExtraQueries(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Dashboard.load(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Canvas.loadWorkpad(appConfig.baseUrl, defaultHeaders))

  setUp(
    scn
      .inject(
        constantConcurrentUsers(20) during (3 * 60), // 1
        rampConcurrentUsers(20) to props.maxUsers during (3 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)

  // generate a closed workload injection profile
  // with levels of 10, 15, 20, 25 and 30 concurrent users
  // each level lasting 10 seconds
  // separated by linear ramps lasting 10 seconds
}
