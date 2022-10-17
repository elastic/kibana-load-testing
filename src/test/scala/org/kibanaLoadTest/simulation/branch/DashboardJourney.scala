package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.Dashboard
import org.kibanaLoadTest.simulation.BaseSimulation

class DashboardJourney extends BaseSimulation {
  val scenarioName = s"DashboardJourney"
  props.maxUsers = 500

  val steps = feed(circularFeeder)
    .exec(session => session.set("Cookie", session("sidValue").as[String]))
    .exec(Dashboard.load(appConfig.baseUrl, defaultHeaders).pause(10))

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
