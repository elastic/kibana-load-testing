package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.Lens
import org.kibanaLoadTest.simulation.BaseSimulation

class LensJourney extends BaseSimulation {
  val scenarioName = "LensJourney"
  props.maxUsers = 30

  val steps = feed(circularFeeder)
    .exec(session => session.set("Cookie", session("sidValue").as[String]))
    .exec(
      Lens
        .load(
          "c762b7a0-f5ea-11eb-a78e-83aac3c38a60",
          appConfig.baseUrl,
          defaultHeaders
        )
        .pause(5)
    )

  val warmupScn: ScenarioBuilder = scenario("warmup").exec(steps)
  val scn: ScenarioBuilder = scenario(scenarioName).exec(steps)

  setUp(
    warmupScn
      .inject(
        constantUsersPerSec(1) during (1 * 15),
        rampUsersPerSec(1) to props.maxUsers during (2 * 60)
      )
      .protocols(httpProtocol)
      .andThen(
        scn
          .inject(
            constantUsersPerSec(props.maxUsers) during (5 * 60)
          )
          .protocols(httpProtocol)
      )
  ).maxDuration(props.simulationTimeout * 2)
}
