package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.{Login, Home, TelemetryAPI}
import org.kibanaLoadTest.simulation.BaseSimulation

class TelemetryAPIJourney extends BaseSimulation {
  def scenarioName(module: String): String = {
    s"Branch telemetry journey $module ${appConfig.buildVersion}"
  }

  val scnTelemetry1: ScenarioBuilder =
    scenario(scenarioName("First hit - non-cached encrypted usage"))
      .feed(circularFeeder)
      .exec(session => session.set("Cookie", session("sidValue").as[String]))
      .exec(TelemetryAPI.load(appConfig.baseUrl, defaultHeaders).pause(1))

  val scnTelemetry2: ScenarioBuilder =
    scenario(scenarioName("Second+ hit - cached encrypted usage"))
      .feed(circularFeeder)
      .exec(session => session.set("Cookie", session("sidValue").as[String]))
      .exec(TelemetryAPI.cached(appConfig.baseUrl, defaultHeaders).pause(1))

  val scnTelemetry3: ScenarioBuilder = scenario(
    scenarioName(
      "Example flyout - non-cached non-encrypted usage, check collectors status"
    )
  ).feed(circularFeeder)
    .exec(session => session.set("Cookie", session("sidValue").as[String]))
    .exec(
      TelemetryAPI
        .getUnencryptedStats(appConfig.baseUrl, defaultHeaders)
        .pause(1)
    )

  val cachedMaxUsers = 250
  val nonCachedMaxUsers = 30
  val duringDuration = 60 * 3

  setUp(
    scnTelemetry1
      .inject(
        constantConcurrentUsers(20) during (duringDuration),
        rampConcurrentUsers(20) to nonCachedMaxUsers during (duringDuration)
      )
      .andThen(
        scnTelemetry2.inject(
          constantConcurrentUsers(20) during (duringDuration),
          rampConcurrentUsers(20) to cachedMaxUsers during (duringDuration)
        )
      )
      .andThen(
        scnTelemetry3.inject(
          constantConcurrentUsers(20) during (duringDuration),
          rampConcurrentUsers(20) to nonCachedMaxUsers during (duringDuration)
        )
      )
  ).protocols(httpProtocol).maxDuration(props.simulationTimeout * 2)
}
