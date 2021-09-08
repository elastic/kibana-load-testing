package org.kibanaLoadTest.simulation.cloud

import io.gatling.core.Predef.{
  atOnceUsers,
  nothingFor,
  openInjectionProfileFactory,
  scenario
}
import io.gatling.core.structure.ScenarioBuilder
import org.kibanaLoadTest.helpers.Helper
import org.kibanaLoadTest.scenario.Discover
import org.kibanaLoadTest.simulation.BaseSimulation

import java.util.Calendar

class DiscoverAtOnce extends BaseSimulation {
  def scenarioName(module: String): String = {
    s"Cloud discover atOnce $module ${appConfig.buildVersion}"
  }

  props.maxUsers = 250

  val scnDiscover1: ScenarioBuilder = scenario(scenarioName("discover query 1"))
    .exec(loginStep.pause(props.loginPause))
    .exec(
      Discover.load(appConfig.baseUrl, defaultHeaders)
    )

  val scnDiscover2: ScenarioBuilder = scenario(scenarioName("discover query 2"))
    .exec(loginStep.pause(props.loginPause))
    .exec(
      Discover
        .doQuery(
          "1",
          appConfig.baseUrl,
          defaultHeaders,
          Helper.getDate(Calendar.DAY_OF_MONTH, -15),
          Helper.getDate(Calendar.DAY_OF_MONTH, 0),
          "fixed_interval:3h"
        )
    )

  val scnDiscover3: ScenarioBuilder = scenario(scenarioName("discover query 3"))
    .exec(loginStep.pause(props.loginPause))
    .exec(
      Discover.doQuery(
        "2",
        appConfig.baseUrl,
        defaultHeaders,
        Helper.getDate(Calendar.DAY_OF_MONTH, -30),
        Helper.getDate(Calendar.DAY_OF_MONTH, 0),
        "calendar_interval:1d"
      )
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
