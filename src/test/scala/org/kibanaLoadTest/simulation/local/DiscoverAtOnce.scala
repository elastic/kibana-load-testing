package org.kibanaLoadTest.simulation.local

import io.gatling.core.Predef.{
  atOnceUsers,
  nothingFor,
  openInjectionProfileFactory,
  scenario
}
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.scenario.Discover
import org.kibanaLoadTest.simulation.BaseSimulation

class DiscoverAtOnce extends BaseSimulation {
  def scenarioName(module: String): String = {
    s"Local  discover atOnce ${module} ${appConfig.buildVersion}"
  }

  props.maxUsers = 400

  val scnDiscover1: ScenarioBuilder = scenario(scenarioName("discover query 1"))
    .exec(loginStep.pause(props.loginPause))
    .exec(
      Discover
        .doQuery(appConfig.baseUrl, defaultHeaders, Discover.discoverPayloadQ1)
    )

  val scnDiscover2: ScenarioBuilder = scenario(scenarioName("discover query 2"))
    .exec(loginStep.pause(props.loginPause))
    .exec(
      Discover
        .doQuery(appConfig.baseUrl, defaultHeaders, Discover.discoverPayloadQ2)
    )

  val scnDiscover3: ScenarioBuilder = scenario(scenarioName("discover query 3"))
    .exec(loginStep.pause(props.loginPause))
    .exec(
      Discover
        .doQuery(appConfig.baseUrl, defaultHeaders, Discover.discoverPayloadQ3)
    )

  setUp(
    scnDiscover1
      .inject(atOnceUsers(props.maxUsers), nothingFor(props.scnPause))
      .andThen(
        scnDiscover2
          .inject(atOnceUsers(props.maxUsers), nothingFor(props.scnPause))
          .andThen(scnDiscover3.inject(atOnceUsers(props.maxUsers)))
      )
  ).protocols(httpProtocol).maxDuration(props.simulationTimeout)
}
