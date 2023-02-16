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
import org.kibanaLoadTest.simulation.generic.core.{ApiCall, JourneyBuilder}
import org.slf4j.{Logger, LoggerFactory}
import spray.json._

import java.nio.file.Files
import java.nio.file.{Path, Paths}
import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.io.Source._
import scala.util.Using
import scala.util.Try

class GenericJourney extends Simulation {
  val logger: Logger = LoggerFactory.getLogger("GenericJourney")

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
  private val skipCleanup = Try(System.getProperty("skipCleanupOnTeardown").toBoolean).getOrElse(false)
  private def isKibanaRootPathDefined: Either[String, String] = {
    if (kibanaRootPath.isEmpty || !Files.exists(Paths.get(kibanaRootPath.get)))
      Left(
        "Loading test data requires Kibana root folder path to be set, use 'KIBANA_DIR' env var"
      )
    else Right(kibanaRootPath.get)
  }

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

  // Building sequence of http requests from journey file
  private val httpSteps = if (journey.needsAuthentication()) {
    // set 'Cookie' header with valid sid value to authenticate request
    config.setCookieHeader = true

    /**
      * It does not make any difference to use unique cookie for individual user (tcp connection), unless we test Kibana
      * security service. Taking it into account, we create a single session and share it.
      */
    val cookiesLst = kbnClient.generateCookies(1)
    val circularFeeder = Iterator
      .continually(cookiesLst.map(i => Map("sid" -> i)))
      .flatten
    feed(circularFeeder)
      .exec(JourneyBuilder.buildHttpSteps(journey.streams, config))
  } else {
    exec(JourneyBuilder.buildHttpSteps(journey.streams, config))
  }

  private val warmupScenario = JourneyBuilder.buildScenario(
    httpSteps,
    s"warmup: ${journey.journeyName} ${config.version}"
  )
  private val testScenario = JourneyBuilder.buildScenario(
    httpSteps,
    s"test: ${journey.journeyName} ${config.version}"
  )

  setUp(
    JourneyBuilder
      .buildPopulation(warmupScenario, journey.scalabilitySetup.warmup)
      .protocols(httpProtocol)
      .andThen(
        JourneyBuilder
          .buildPopulation(testScenario, journey.scalabilitySetup.test)
          .protocols(httpProtocol)
      )
  ).maxDuration(JourneyBuilder.getDuration(journey.scalabilitySetup.maxDuration))

  before {
    if (journey.testData.isDefined) {
      isKibanaRootPathDefined match {
        case Right(path) =>
          testDataLoader(
            journey.testData.get,
            path,
            kbnClient.load,
            esArchiver.load
          )
        case Left(error) => throw new IllegalArgumentException(error)
      }
    }
  }

  // Using 'after' hook to cleanup Kibana/Elasticsearch after journey run
  after {
    if (skipCleanup) {
      logger.warn("!!! Unloading archives for ES/Kibana is skipped !!!")
    } else {
      if (journey.testData.isDefined) {
        testDataLoader(
          journey.testData.get,
          kibanaRootPath.get,
          kbnClient.unload,
          esArchiver.unload
        )
      }
    }
  }
}
