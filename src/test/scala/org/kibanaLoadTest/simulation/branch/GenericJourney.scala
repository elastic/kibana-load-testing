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
      case "GET" => http(requestName = s"${request.method} ${request.path}")
        .get(request.path)
        .headers(headers)
        .check(status.is(request.statusCode))
      case "POST" => http(requestName = s"${request.method} ${request.path}")
        .post(request.path)
        .body(StringBody(request.body.get)).asJson
        .headers(headers)
        .check(status.is(request.statusCode))
      case "PUT" => http(requestName = s"${request.method} ${request.path}")
        .put(request.path)
        .headers(headers)
        .check(status.is(request.statusCode))
      case "DELETE" => http(requestName = s"${request.method} ${request.path}")
        .delete(request.path)
        .headers(headers)
        .check(status.is(request.statusCode))
      case _ => throw new IllegalArgumentException(s"Invalid method ${request.method}")
    }
    exec(httpRequestBuilder)
  }
}

class GenericJourney extends Simulation {
  def getDuration(duration: String) = {
    duration.takeRight(1) match {
      case "s" =>  duration.dropRight(1).toInt
      case "m" => duration.dropRight(1).toInt * 60
      case _ => throw new IllegalArgumentException(s"Invalid duration format: ${duration}")
    }
  }

  def scenarioSteps(journey: Journey, config: KibanaConfiguration):ChainBuilder = {
    var steps: ChainBuilder = exec()

    //TODO: Do we need to filter out static page requests?
    val tracesAPICallsOnly = journey.requests.filter(_.request.path.matches("(\\/api|\\/internal).*"))

    var priorRequest: Option[org.kibanaLoadTest.simulation.branch.Request] = Option.empty
    for (trace <- tracesAPICallsOnly) {
      val request = trace.request
      val priorTimestamp = priorRequest.map(_.timestamp).getOrElse(request.timestamp)
      val pauseDuration = Math.max(0L, request.timestamp.getTime - priorTimestamp.getTime)
      if (pauseDuration > 0L) {
        steps = steps.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
      }

      steps = steps.exec(ApiCall.execute(request, config))
      priorRequest = Option(request)
    }
    steps
  }

  def scenarioForStage(steps: ChainBuilder, scenarioName: String): ScenarioBuilder = {
    scenario(scenarioName).exec(steps)
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

  private val journeyPath: String = System.getProperty("journeyPath")
  val fileExists = new java.io.File(journeyPath).isFile

  if (!fileExists || !journeyPath.endsWith(".json")) {
    throw new IllegalArgumentException(s"Provide path to valid json journey file using journeyPath system var, found '$journeyPath'")
  }
  private val journeyFile = fromFile(journeyPath).getLines mkString "\n"
  private val envConfig: String = System.getProperty("env", "config/local.conf")
  val appConfig: KibanaConfiguration = new KibanaConfiguration(Helper.readResourceConfigFile(envConfig))
    .syncWithInstance()

  val httpHelper = new HttpHelper(appConfig)
  var httpProtocol: HttpProtocolBuilder = httpHelper.getProtocol.baseUrl(appConfig.baseUrl)


  private val journey = journeyFile.parseJson.convertTo[Journey]

  private val steps = scenarioSteps(journey, appConfig)
  private val warmupScenario = scenarioForStage(steps, s"warmup for ${journey.journeyName} ${appConfig.version}")
  private val testScenario = scenarioForStage(steps, s"test for ${journey.journeyName} ${appConfig.version}")

  setUp(
    populationForStage(warmupScenario, journey.scalabilitySetup.warmup)
      .protocols(httpProtocol)
      .andThen(
        populationForStage(testScenario, journey.scalabilitySetup.test)
          .protocols(httpProtocol)
      )
  ).maxDuration(getDuration(journey.scalabilitySetup.maxDuration))
}
