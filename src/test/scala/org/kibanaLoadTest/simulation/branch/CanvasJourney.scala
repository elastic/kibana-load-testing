package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.Predef.{
  constantConcurrentUsers,
  rampConcurrentUsers,
  scenario
}
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.Canvas
import org.kibanaLoadTest.simulation.BaseSimulation

class CanvasJourney extends BaseSimulation {
  val scenarioName = "CanvasJourney"
  props.maxUsers = 100

  val steps = feed(circularFeeder)
    .exec(session => session.set("Cookie", session("sidValue").as[String]))
    .exec(Canvas.loadWorkpad(appConfig.baseUrl, defaultHeaders))

  val warmupScn: ScenarioBuilder = scenario("warmup").exec(steps)
  val scn: ScenarioBuilder = scenario(scenarioName).exec(steps)

  setUp(
    warmupScn
      .inject(
        constantUsersPerSec(20) during (1 * 30),
        rampUsersPerSec(20) to props.maxUsers during (3 * 60)
      )
      .protocols(httpProtocol)
      .andThen(
        scn
          .inject(
            constantConcurrentUsers(props.maxUsers) during (5 * 60)
          )
          .protocols(httpProtocol)
      )
  ).maxDuration(props.simulationTimeout * 2)
}
