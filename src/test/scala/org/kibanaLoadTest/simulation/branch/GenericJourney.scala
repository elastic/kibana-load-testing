package org.kibanaLoadTest.simulation.branch

import io.gatling.core.Predef.{exec, _}
import io.gatling.core.structure.{ChainBuilder, PopulationBuilder, ScenarioBuilder}
import io.gatling.http.Predef.{http, status, _}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{Helper, HttpHelper}
import org.kibanaLoadTest.simulation.branch.JourneyJsonProtocol._

import scala.collection.mutable.ListBuffer
import spray.json._
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import org.kibanaLoadTest.scenario.Login.loginHeaders

import java.util.concurrent.TimeUnit
import scala.io.Source._

object ApiCall {
  def execute(request: org.kibanaLoadTest.simulation.branch.Request, config: KibanaConfiguration): ChainBuilder = {
    val defaultHeaders = request.headers.-(
      "content-length",
      "Content-Length",
      "Kbn-Version",
      "Traceparent",
      "Authorization",
      "Cookie",
      "X-Kbn-Context"
    )

    val headers = if (request.headers.contains("Kbn-Version")) defaultHeaders + ("Kbn-Version" -> config.version) else defaultHeaders

    val httpRequestBuilder = request.method match {
      case "GET" => http(requestName = request.path).get(request.path)
      case "POST" => http(requestName = request.path).post(request.path)
      case "PUT" => http(requestName = request.path).put(request.path)
      case "DELETE" => http(requestName = request.path).delete(request.path)
      case _ => throw new IllegalArgumentException(s"Invalid method ${request.method}")
    }

    if (request.body.isDefined) httpRequestBuilder.body(StringBody(request.body.get))

    httpRequestBuilder.headers(headers).check(status.is(request.statusCode))

    exec(httpRequestBuilder)
  }
}

class GenericJourney extends Simulation {
  def scenarioForStage(journey: Journey, stageName: String, config: KibanaConfiguration): ScenarioBuilder = {
    var scn: ScenarioBuilder = scenario(s"${stageName} for ${journey.journeyName} ${appConfig.version}")

    var priorRequest: Option[org.kibanaLoadTest.simulation.branch.Request] = Option.empty
    for (trace <- journey.requests) {
      val request = trace.request
      val priorTimestamp = priorRequest.map(_.timestamp).getOrElse(request.timestamp)
      //TODO: Ensure that we return dates in this format in JSON (millis since epoch)
      //TODO: Move convertion to deserialization?
      val pauseDuration = Math.max(0L, Helper.convertDateToTimestamp(request.timestamp) - Helper.convertDateToTimestamp(priorTimestamp))
      if (pauseDuration > 0L) {
        scn = scn.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
      }

      scn = scn.exec(ApiCall.execute(request, config))
      priorRequest = Option(request)
    }
    scn
  }

  def getDuration(duration: String) = {
    duration.takeRight(1) match {
      case "s" =>  duration.dropRight(1).toInt
      case "m" => duration.dropRight(1).toInt * 60
      case _ => throw new IllegalArgumentException(s"Invalid duration format: ${duration}")
    }
  }

  def populationForStage(scn: ScenarioBuilder, stage: List[Step]): PopulationBuilder = {
    val steps = ListBuffer[ClosedInjectionStep]()
    for (step <- stage) {
      val injectionStep = step.action match {
        case "constantConcurrentUsers" => constantConcurrentUsers(step.maxUsersCount) during (getDuration(step.duration))
        case "rampConcurrentUsers" => rampConcurrentUsers(step.minUsersCount.get) to step.maxUsersCount during (getDuration(step.duration))
        case _ => throw new IllegalArgumentException(s"Invalid action: ${step.action}")
      }
      steps.addOne(injectionStep)
    }
    scn.inject(steps)
  }


  // TODO: Read this from an external file later on
  //private val journeyFile = fromFile("journey.json").getLines mkString "\n"
  private val journeyFile = fromResource("journey.json").getLines mkString "\n"
  private val envConfig: String = System.getProperty("env", "config/local.conf")
  val appConfig: KibanaConfiguration = new KibanaConfiguration(Helper.readResourceConfigFile(envConfig))
    .syncWithInstance()

  val httpHelper = new HttpHelper(appConfig)
  var httpProtocol: HttpProtocolBuilder = httpHelper.getProtocol.baseUrl(appConfig.baseUrl)


  private val journey = journeyFile.parseJson.convertTo[Journey]

  private val warmupScenario = scenarioForStage(journey, "warmup", appConfig)
  private val testScenario = scenarioForStage(journey, "test", appConfig)

  setUp(
    populationForStage(warmupScenario, journey.scalabilitySetup.test)
      .protocols(httpProtocol)
      .andThen(
        populationForStage(testScenario, journey.scalabilitySetup.test)
          .protocols(httpProtocol)
      )
  ).maxDuration(getDuration(journey.scalabilitySetup.maxDuration))
}
