package org.kibanaLoadTest.simulation.generic

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import io.gatling.core.structure.{
  ChainBuilder,
  PopulationBuilder,
  ScenarioBuilder
}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper
import org.kibanaLoadTest.helpers.HttpHelper
import org.kibanaLoadTest.simulation.generic
import org.kibanaLoadTest.simulation.generic.mapping.{Step, Journey}
import org.kibanaLoadTest.simulation.generic.mapping.JourneyJsonProtocol._
import spray.json._

import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.io.Source._
import scala.util.Using

object ApiCall {
  def execute(
      requests: List[mapping.Request],
      config: KibanaConfiguration
  ): ChainBuilder = {
    // Workaround for https://github.com/gatling/gatling/issues/3783
    val parent :: children = requests
    val httpParentRequest = httpRequest(parent.http, config)
    if (children.isEmpty) {
      exec(httpParentRequest)
    } else {
      val childHttpRequests: Seq[HttpRequestBuilder] =
        (children.map(request => httpRequest(request.http, config))).toSeq
      exec(httpParentRequest.resources(childHttpRequests: _*))
    }
  }

  def httpRequest(
      request: mapping.Http,
      config: KibanaConfiguration
  ): HttpRequestBuilder = {
    val excludeHeaders = List(
      "Content-Length",
      "Kbn-Version",
      "Traceparent",
      "Authorization",
      "Cookie",
      "X-Kbn-Context"
    )
    val defaultHeaders = request.headers.--(excludeHeaders.iterator)

    val headers =
      if (request.headers.contains("Kbn-Version"))
        defaultHeaders + ("Kbn-Version" -> config.buildVersion)
      else defaultHeaders
    val requestName = s"${request.method} ${request.path
      .replaceAll(".+?(?=\\/bundles)", "") + request.query.getOrElse("")}"
    val url = request.path + request.query.getOrElse("")
    request.method match {
      case "GET" =>
        http(requestName)
          .get(url)
          .headers(headers)
          .check(status.is(request.statusCode))
      case "POST" =>
        request.body match {
          case Some(value) =>
            // https://gatling.io/docs/gatling/reference/current/http/request/#stringbody
            // Gatling uses #{value} syntax to pass session values, we disable it by replacing # with ##
            // $ was deprecated, but Gatling still identifies it as session attribute
            val bodyString = value.replace("#", "##").replace("$", "$$")
            http(requestName)
              .post(url)
              .body(StringBody(bodyString))
              .asJson
              .headers(headers)
              .check(status.is(request.statusCode))
          case _ =>
            http(requestName)
              .post(url)
              .asJson
              .headers(headers)
              .check(status.is(request.statusCode))
        }

      case "PUT" =>
        http(requestName)
          .put(url)
          .headers(headers)
          .check(status.is(request.statusCode))
      case "DELETE" =>
        http(requestName)
          .delete(url)
          .headers(headers)
          .check(status.is(request.statusCode))
      case _ =>
        throw new IllegalArgumentException(s"Invalid method ${request.method}")
    }
  }
}

class GenericJourney extends Simulation {
  def getDuration(duration: String) = {
    duration.takeRight(1) match {
      case "s" => duration.dropRight(1).toInt
      case "m" => duration.dropRight(1).toInt * 60
      case _ =>
        throw new IllegalArgumentException(
          s"Invalid duration format: ${duration}"
        )
    }
  }

  def scenarioSteps(
      journey: Journey,
      config: KibanaConfiguration
  ): ChainBuilder = {
    var steps: ChainBuilder = exec()
    var priorStream: Option[mapping.RequestStream] =
      Option.empty
    for (stream <- journey.streams) {
      val priorDate =
        priorStream.map(_.endTime).getOrElse(stream.endTime)
      val pauseDuration =
        Math.max(0L, stream.startTime.getTime - priorDate.getTime)
      if (pauseDuration > 0L) {
        steps = steps.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
      }

      if (!stream.requests.isEmpty) {
        steps = steps.exec(ApiCall.execute(stream.requests, config))
        priorStream = Option(stream)
      }
    }
    steps
  }

  def scenarioForStage(
      steps: ChainBuilder,
      scenarioName: String
  ): ScenarioBuilder = {
    scenario(scenarioName).exec(steps)
  }

  def populationForStage(
      scn: ScenarioBuilder,
      stage: List[Step]
  ): PopulationBuilder = {
    val steps = ListBuffer[ClosedInjectionStep]()
    for (step <- stage) {
      val injectionStep = step.action match {
        case "constantConcurrentUsers" =>
          constantConcurrentUsers(step.maxUsersCount) during (getDuration(
            step.duration
          ))
        case "rampConcurrentUsers" =>
          rampConcurrentUsers(
            step.minUsersCount.get
          ) to step.maxUsersCount during (getDuration(step.duration))
        case _ =>
          throw new IllegalArgumentException(s"Invalid action: ${step.action}")
      }
      steps.addOne(injectionStep)
    }
    scn.inject(steps)
  }

  private val journeyJson =
    Using(fromFile(Helper.loadJsonFile("journeyPath"))) { f =>
      f.getLines().mkString("\n")
    }
  private val journey = journeyJson.get.parseJson.convertTo[Journey]

  // Default values are valid as long we use FTR to start Kibana server
  private val kibanaHost =
    sys.env.getOrElse("KIBANA_HOST", "http://localhost:5620")
  private val esHost = sys.env.getOrElse("ES_URL", "http://localhost:9220")
  private val providerType = sys.env.getOrElse("AUTH_PROVIDER_TYPE", "basic")
  private val providerName = sys.env.getOrElse("AUTH_PROVIDER_NAME", "basic")
  private val username = sys.env.getOrElse("AUTH_LOGIN", "elastic")
  private val password = sys.env.getOrElse("AUTH_PASSWORD", "changeme")

  private val config: KibanaConfiguration = new KibanaConfiguration(
    kibanaHost,
    esHost,
    username,
    password,
    providerType,
    providerName
  )
  private val httpProtocol: HttpProtocolBuilder =
    new HttpHelper(config).getProtocol
      .baseUrl(config.baseUrl)
      .disableUrlEncoding
      // Gatling automatically follow redirects in case of 301, 302, 303, 307 or 308 response status code
      // Disabling this behavior since we run the defined sequence of requests
      .disableFollowRedirect
  private val steps = scenarioSteps(journey, config)
  private val warmupScenario = scenarioForStage(
    steps,
    s"warmup for ${journey.journeyName} ${config.version}"
  )
  private val testScenario = scenarioForStage(
    steps,
    s"test for ${journey.journeyName} ${config.version}"
  )

  setUp(
    populationForStage(warmupScenario, journey.scalabilitySetup.warmup)
      .protocols(httpProtocol)
      .andThen(
        populationForStage(testScenario, journey.scalabilitySetup.test)
          .protocols(httpProtocol)
      )
  ).maxDuration(getDuration(journey.scalabilitySetup.maxDuration))
}
