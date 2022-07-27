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
      request: mapping.Request,
      config: KibanaConfiguration
  ): ChainBuilder = {
    val defaultHeaders = request.headers.-(
      "Content-Length",
      "Kbn-Version",
      "Traceparent",
      "Authorization",
      "Cookie",
      "X-Kbn-Context"
    )
    val headers =
      if (request.headers.contains("Kbn-Version"))
        defaultHeaders + ("Kbn-Version" -> config.version)
      else defaultHeaders
    val httpRequestBuilder = request.method match {
      case "GET" =>
        http(requestName = s"${request.method} ${request.path}")
          .get(request.path)
          .headers(headers)
          .check(status.is(request.statusCode))
      case "POST" =>
        http(requestName = s"${request.method} ${request.path}")
          .post(request.path)
          .body(StringBody(request.body.get))
          .asJson
          .headers(headers)
          .check(status.is(request.statusCode))
      case "PUT" =>
        http(requestName = s"${request.method} ${request.path}")
          .put(request.path)
          .headers(headers)
          .check(status.is(request.statusCode))
      case "DELETE" =>
        http(requestName = s"${request.method} ${request.path}")
          .delete(request.path)
          .headers(headers)
          .check(status.is(request.statusCode))
      case _ =>
        throw new IllegalArgumentException(s"Invalid method ${request.method}")
    }
    exec(httpRequestBuilder)
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
    var priorRequest: Option[mapping.Request] =
      Option.empty
    for (trace <- journey.requests) {
      val request = trace.request
      val priorTimestamp =
        priorRequest.map(_.timestamp).getOrElse(request.timestamp)
      val pauseDuration =
        Math.max(0L, request.timestamp.getTime - priorTimestamp.getTime)
      if (pauseDuration > 0L) {
        steps = steps.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
      }

      steps = steps.exec(ApiCall.execute(request, config))
      priorRequest = Option(request)
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
    System.getProperty("KIBANA_HOST", "http://localhost:5620")
  private val esHost = System.getProperty("ES_HOST", "http://localhost:9220")
  private val providerType = System.getProperty("AUTH_PROVIDER_TYPE", "basic")
  private val providerName = System.getProperty("AUTH_PROVIDER_NAME", "basic")
  private val username = System.getProperty("AUTH_LOGIN", "elastic")
  private val password = System.getProperty("AUTH_PASSWORD", "changeme")

  private val config: KibanaConfiguration = new KibanaConfiguration(
    kibanaHost,
    journey.kibanaVersion,
    esHost,
    username,
    password,
    providerType,
    providerName
  )
  private val httpProtocol: HttpProtocolBuilder =
    new HttpHelper(config).getProtocol
      .baseUrl(config.baseUrl)
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
