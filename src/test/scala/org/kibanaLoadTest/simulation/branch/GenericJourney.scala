package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef.{exec, _}
import io.gatling.core.structure.{PopulationBuilder, ScenarioBuilder}
import io.gatling.http.Predef.{http, status, _}
import org.kibanaLoadTest.scenario.Login
import org.kibanaLoadTest.simulation.BaseSimulation
import org.kibanaLoadTest.simulation.branch.JourneyJsonProtocol._
import spray.json._

import java.util.concurrent.TimeUnit
import scala.io.Source._

object ApiCall {
  def execute(defaultHeaders: Map[String, String], request: org.kibanaLoadTest.simulation.branch.Request) = {
    val headers = defaultHeaders
    exec(
      http(requestName = request.path)
        .post(request.path)
        .body(StringBody(request.body))
        .headers(headers)
        .check(status.is(request.status))
    )
  }
}

class GenericJourney extends BaseSimulation {
  def scenarioForStage(journey: Journey, stageName: String): ScenarioBuilder = {
    var scn: ScenarioBuilder = scenario(s"${stageName} for ${journey.name}")
      .exec(Login
          .doLogin(
            appConfig.isSecurityEnabled,
            appConfig.loginPayload,
            appConfig.loginStatusCode
          )
      )
      // TODO: Why this pause?
      .pause(5)

    var priorRequest: Option[org.kibanaLoadTest.simulation.branch.Request] = Option.empty
    for (request <- journey.requests) {
      val priorTimestamp = priorRequest.map(_.timestamp).getOrElse(request.timestamp)
      //TODO: Ensure that we return dates in this format in JSON (millis since epoch)
      val pauseDuration = Math.max(0L, request.timestamp.getTime - priorTimestamp.getTime)
      if (pauseDuration > 0L) {
        scn = scn.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
      }

      scn = scn.exec(ApiCall.execute(defaultHeaders, request))
      priorRequest = Option(request)
    }
    scn
  }

  def populationForStage(scn: ScenarioBuilder, stage: Stage): PopulationBuilder = {
    stage.action match {
      case "constantConcurrentUsers" => scn.inject(constantConcurrentUsers(stage.maxUserCount) during (stage.duration))
      case "rampConcurrentUsers" => scn.inject(rampConcurrentUsers(stage.minUserCount.get) to stage.maxUserCount during (stage.duration))
      case _ => throw new IllegalArgumentException(s"Invalid action ${stage.action}")
    }
  }


  // TODO: Read this from an external file later on
  //private val journeyFile = fromFile("journey.json").getLines mkString "\n"
  private val journeyFile = fromResource("journey.json").getLines mkString "\n"

  private val journey = journeyFile.parseJson.convertTo[Journey]

  private val warmupScenario = scenarioForStage(journey, "warmup")
  private val testScenario = scenarioForStage(journey, "test")

  setUp(
    populationForStage(warmupScenario, journey.scalabilitySetup.warmup)
      .protocols(httpProtocol)
      .andThen(
        populationForStage(testScenario, journey.scalabilitySetup.test)
          .protocols(httpProtocol)
      )
    //TODO: Set this from the journey?
  ).maxDuration(props.simulationTimeout * 1)
}
