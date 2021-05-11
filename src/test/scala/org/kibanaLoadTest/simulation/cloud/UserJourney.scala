package org.kibanaLoadTest.simulation.cloud

import io.gatling.core.Predef.{
  constantConcurrentUsers,
  rampConcurrentUsers,
  scenario
}
import org.kibanaLoadTest.simulation.BaseSimulation
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.Login.{doLogin, openKibana}
import org.kibanaLoadTest.scenario.Home.loadHome

class UserJourney extends BaseSimulation {
  val scenarioName = s"User journey ${appConfig.buildVersion}"

  val scn: ScenarioBuilder = scenario(scenarioName)
    .exec(
      openKibana
        .pause(5)
        .exec(
          doLogin(
            appConfig.isSecurityEnabled,
            appConfig.loginPayload,
            appConfig.loginStatusCode
          )
        )
        .exec(loadHome(appConfig.baseUrl))
    )

  setUp(
    scn
      .inject(
        constantConcurrentUsers(10) during (1 * 60), // 1
        rampConcurrentUsers(10) to props.maxUsers during (4 * 60) // 2
      )
      .protocols(httpProtocol)
  ).maxDuration(props.simulationTimeout * 2)

  // generate a closed workload injection profile
  // with levels of 10, 15, 20, 25 and 30 concurrent users
  // each level lasting 10 seconds
  // separated by linear ramps lasting 10 seconds
}
