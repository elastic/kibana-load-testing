package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.helpers.KbnClient
import org.kibanaLoadTest.scenario.Discover
import org.kibanaLoadTest.simulation.BaseSimulation

class DiscoverJourney extends BaseSimulation {
  val scenarioName = "DiscoverJourney"
  props.maxUsers = 500
  val client = new KbnClient(appConfig)
  val cookiesLst = client.generateCookies(50)
  val circularFeeder = Iterator
    .continually(cookiesLst.map(i => Map("sidValue" -> i)))
    .flatten

  val steps = feed(circularFeeder)
    .exec(session => session.set("Cookie", session("sidValue").as[String]))
    .exec(Discover.load(appConfig.baseUrl, defaultHeaders).pause(10))
    .exec(Discover.do2ExtraQueries(appConfig.baseUrl, defaultHeaders).pause(10))

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
