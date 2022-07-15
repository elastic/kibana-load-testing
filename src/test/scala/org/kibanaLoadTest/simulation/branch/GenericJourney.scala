package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef.{exec, _}
import io.gatling.core.structure.{PopulationBuilder, ScenarioBuilder}
import io.gatling.http.Predef.{http, status, _}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{Helper, HttpHelper}
import org.kibanaLoadTest.simulation.branch.JourneyJsonProtocol._
import spray.json._

import java.util.concurrent.TimeUnit
import scala.io.Source._

object ApiCall {
  def execute(request: org.kibanaLoadTest.simulation.branch.Request) = {
    request.method match {
      case "POST" => exec(
        http(requestName = request.path)
          .post(request.path)
          .body(StringBody(request.body))
          .headers(request.headers)
          .check(status.is(request.status))
      )
      case "GET" => exec(
        http(requestName = request.path)
          .get(request.path)
          .headers(request.headers)
          .check(status.is(request.status))
      )
      case _ => throw new IllegalArgumentException(s"Invalid method ${request.method}")
    }
  }
}

class GenericJourney extends Simulation {
  def scenarioForStage(journey: Journey, stageName: String): ScenarioBuilder = {
    var scn: ScenarioBuilder = scenario(s"${stageName} for ${journey.name}")

    var priorRequest: Option[org.kibanaLoadTest.simulation.branch.Request] = Option.empty
    for (request <- journey.requests) {
      val priorTimestamp = priorRequest.map(_.timestamp).getOrElse(request.timestamp)
      //TODO: Ensure that we return dates in this format in JSON (millis since epoch)
      val pauseDuration = Math.max(0L, request.timestamp.getTime - priorTimestamp.getTime)
      if (pauseDuration > 0L) {
        scn = scn.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
      }

      scn = scn.exec(ApiCall.execute(request))
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
  private val envConfig: String = System.getProperty("env", "config/local.conf")
  private val baseUrl: String = "http://localhost:5620"
  val appConfig: KibanaConfiguration = new KibanaConfiguration(Helper.readResourceConfigFile(envConfig))
    .syncWithInstance()

  val httpHelper = new HttpHelper(appConfig)
  var httpProtocol: HttpProtocolBuilder = httpHelper.getProtocol.baseUrl(baseUrl)

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
  ).maxDuration(journey.scalabilitySetup.maxDuration)
}
