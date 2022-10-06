package org.kibanaLoadTest.simulation.generic

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.{
  ChainBuilder,
  PopulationBuilder,
  ScenarioBuilder
}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{ESArchiver, Helper, HttpHelper, KbnClient}
import org.kibanaLoadTest.simulation.generic
import org.kibanaLoadTest.simulation.generic.mapping.{Journey, Step, TestData}
import org.kibanaLoadTest.simulation.generic.mapping.JourneyJsonProtocol._
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import java.nio.file.Files
import java.nio.file.{Path, Paths}
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

    var headers = defaultHeaders
    if (request.headers.contains("Kbn-Version")) {
      headers += ("Kbn-Version" -> config.buildVersion)
    }
    if (request.headers.contains("Cookie")) {
      headers += ("Cookie" -> "#{Cookie}")
    }
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
            val bodyString = value.replaceAll("\\$\\{\\{", "{{")
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
  val logger: Logger = LoggerFactory.getLogger("GenericJourney")
  val modelsMap = Map(
    "constantConcurrentUsers" -> "closed",
    "rampConcurrentUsers" -> "closed",
    "atOnceUsers" -> "open",
    "rampUsers" -> "open",
    "rampUsersPerSec" -> "open",
    "constantUsersPerSec" -> "open",
    "stressPeakUsers" -> "open"
  )

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

  def closedModelStep(step: Step): ClosedInjectionStep = {
    logger.info(s"Closed model: building ${step.toString}")
    step.action match {
      case "constantConcurrentUsers" =>
        constantConcurrentUsers(step.userCount.get) during (getDuration(
          step.duration.get
        ))
      case "rampConcurrentUsers" =>
        rampConcurrentUsers(
          step.minUsersCount.get
        ) to step.maxUsersCount.get during (getDuration(step.duration.get))
      case _ =>
        throw new IllegalArgumentException(
          s"Invalid closed model: ${step.action}"
        )
    }
  }

  def openModelStep(step: Step): OpenInjectionStep = {
    logger.info(s"Open model: building${step.toString}")
    step.action match {
      case "atOnceUsers" =>
        atOnceUsers(step.userCount.get)
      case "rampUsers" =>
        rampUsers(step.userCount.get).during(getDuration(step.duration.get))
      case "constantUsersPerSec" =>
        constantUsersPerSec(step.userCount.get.toDouble)
          .during(getDuration(step.duration.get))
      case "stressPeakUsers" =>
        stressPeakUsers(step.userCount.get)
          .during(getDuration(step.duration.get))
      case "rampUsersPerSec" =>
        rampUsersPerSec(step.minUsersCount.get)
          .to(step.maxUsersCount.get)
          .during(getDuration(step.duration.get))
      case _ =>
        throw new IllegalArgumentException(
          s"Invalid open model: ${step.action}"
        )
    }
  }

  def populationForStage(
      scn: ScenarioBuilder,
      steps: List[Step]
  ): PopulationBuilder = {
    logger.info(s"Building population for '${scn.name}' scenario")
    if (steps.isEmpty) {
      throw new IllegalArgumentException(
        s"'${scn.name}': injection steps must be defined"
      )
    }
    val models = steps
      .map(step =>
        modelsMap.getOrElse(
          step.action,
          throw new IllegalArgumentException(
            s"Injection model '${step.action}' is not supported by GenericJourney"
          )
        )
      )
      .distinct
    if (models.length > 1) {
      throw new IllegalArgumentException(
        s"'${scn.name}': closed and open models shouldn't be used in the same stage, fix scalabilitySetup:\n${modelsMap.toString()}"
      )
    }
    models.head match {
      case "open"   => scn.inject(steps.map(step => openModelStep(step)))
      case "closed" => scn.inject(steps.map(step => closedModelStep(step)))
      case _ =>
        throw new IllegalArgumentException("Unknown step model")
    }
  }

  def testDataLoader(
      testData: TestData,
      kibanaRootPath: String,
      kbnClientCallback: Path => Unit,
      esArchiverCallback: Path => Unit
  ): Unit = {
    if (testData.esArchives.isDefined) {
      testData.esArchives.get.foreach(archiveRelativePath => {
        esArchiverCallback(Paths.get(kibanaRootPath, archiveRelativePath))
      })
    }
    if (testData.kbnArchives.isDefined) {
      testData.kbnArchives.get.foreach(archiveRelativePath => {
        kbnClientCallback(Paths.get(kibanaRootPath, archiveRelativePath))
      })
    }
  }

  private val journeyJson =
    Using(fromFile(Helper.loadFile("journeyPath"))) { f =>
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
  private val kibanaRootPath = sys.env.get("KIBANA_DIR")

  private val config: KibanaConfiguration = new KibanaConfiguration(
    kibanaHost,
    esHost,
    username,
    password,
    providerType,
    providerName
  )
  val esArchiver = new ESArchiver(config)
  val kbnClient = new KbnClient(config)
  private val httpProtocol: HttpProtocolBuilder =
    new HttpHelper(config).getProtocol
      .baseUrl(config.baseUrl)
      .disableUrlEncoding
      // Gatling automatically follow redirects in case of 301, 302, 303, 307 or 308 response status code
      // Disabling this behavior since we run the defined sequence of requests
      .disableFollowRedirect
  private val steps = if (journey.journeyName.contains("login")) {
    exec(scenarioSteps(journey, config))
  } else {
    val cookiesLst =
      kbnClient.generateCookies(
        journey.scalabilitySetup.getMaxConcurrentUsers()
      )
    val circularFeeder = Iterator
      .continually(cookiesLst.map(i => Map("sidValue" -> i)))
      .flatten
    feed(circularFeeder)
      .exec(session => session.set("Cookie", session("sidValue").as[String]))
      .exec(scenarioSteps(journey, config))
  }

  private val warmupScenario = scenarioForStage(
    steps,
    s"warmup for ${journey.journeyName} ${config.version}"
  )
  private val testScenario = scenarioForStage(
    steps,
    s"test for ${journey.journeyName} ${config.version}"
  )

  private val testData = journey.testData
  if (testData.isDefined) {
    if (
      kibanaRootPath.isEmpty || !Files.exists(Paths.get(kibanaRootPath.get))
    ) {
      throw new IllegalArgumentException(
        s"Loading test data requires Kibana root folder path to be set, use 'KIBANA_DIR' env var"
      )
    }
    testDataLoader(
      testData.get,
      kibanaRootPath.get,
      kbnClient.load,
      esArchiver.load
    )
  }

  setUp(
    populationForStage(warmupScenario, journey.scalabilitySetup.warmup)
      .protocols(httpProtocol)
      .andThen(
        populationForStage(testScenario, journey.scalabilitySetup.test)
          .protocols(httpProtocol)
      )
  ).maxDuration(getDuration(journey.scalabilitySetup.maxDuration))

  // Using 'after' hook to cleanup Elasticsearch after journey run
  after {
    if (testData.isDefined) {
      testDataLoader(
        testData.get,
        kibanaRootPath.get,
        kbnClient.unload,
        esArchiver.unload
      )
    }
  }
}
