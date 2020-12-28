package org.kibanaLoadTest.simulation

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Canvas, Dashboard, Discover, Login}
import scala.concurrent.duration.DurationInt

class DemoJourney extends BaseSimulation {
  val scenarioName = s"Kibana demo journey ${appConfig.buildVersion}"

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
        constantConcurrentUsers(20) during (3 minutes), // 1
        rampConcurrentUsers(20) to 50 during (3 minutes) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(15 minutes)

  // generate a closed workload injection profile
  // with levels of 10, 15, 20, 25 and 30 concurrent users
  // each level lasting 10 seconds
  // separated by linear ramps lasting 10 seconds
}
