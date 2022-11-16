package org.kibanaLoadTest.simulation.generic.core

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.{
  ChainBuilder,
  PopulationBuilder,
  ScenarioBuilder
}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.simulation.generic.mapping
import org.kibanaLoadTest.simulation.generic.mapping.{RequestStream, Step}
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit

object JourneyBuilder {
  val logger: Logger = LoggerFactory.getLogger("Scenario")
  val modelsMap = Map(
    "constantConcurrentUsers" -> "closed",
    "rampConcurrentUsers" -> "closed",
    "atOnceUsers" -> "open",
    "rampUsers" -> "open",
    "rampUsersPerSec" -> "open",
    "constantUsersPerSec" -> "open",
    "stressPeakUsers" -> "open",
    "incrementUsersPerSec" -> "open"
  )

  /**
    * Converts string to Gatling duration format
    * @param duration string in 'xs'/'xm' format, e.g. '30s' or '5m'
    * @return milliseconds
    */
  def getDuration(duration: String): Int = {
    duration.takeRight(1) match {
      case "s" => duration.dropRight(1).toInt
      case "m" => duration.dropRight(1).toInt * 60
      case _ =>
        throw new IllegalArgumentException(
          s"Invalid duration format: ${duration}"
        )
    }
  }

  private def getClosedInjectionStep(step: Step): ClosedInjectionStep = {
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

  private def getOpenInjectionStep(step: Step): OpenInjectionStep = {
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
      case "incrementUsersPerSec" =>
        incrementUsersPerSec(step.userCount.get.toDouble)
          .times(step.times.get)
          .eachLevelLasting(getDuration(step.duration.get))
          .separatedByRampsLasting(getDuration(step.duration.get))
          .startingFrom(step.userCount.get.toDouble)

      case _ =>
        throw new IllegalArgumentException(
          s"Invalid open model: ${step.action}"
        )
    }
  }

  /**
    * Builds a chain of http requests
    * @param streams list of journey request streams
    * @param config kibana configuration
    * @return ChainBuilder that can be passed into Gatling scenario
    */
  def buildHttpSteps(
      streams: List[RequestStream],
      config: KibanaConfiguration
  ): ChainBuilder = {
    var steps: ChainBuilder = exec()
    var priorStream: Option[mapping.RequestStream] =
      Option.empty

    if (streams.length == 1) {
      steps = steps.exec(ApiCall.execute(streams(0).requests, config))
    } else {
      for (stream <- streams) {
        val priorDate =
          priorStream.map(_.endTime).getOrElse(stream.endTime)
        val pauseDuration =
          Math.max(0L, stream.startTime.get.getTime - priorDate.get.getTime)
        if (pauseDuration > 0L) {
          steps = steps.pause(pauseDuration.toString, TimeUnit.MILLISECONDS)
        }

        // temporary filter out some requests
        // https://github.com/elastic/kibana/issues/143557
        val exludeUrls = Array("/api/status", "/api/saved_objects/_import")
        val requests =
          stream.requests.filter(req =>
            !exludeUrls.contains(req.getRequestUrl())
          )

        if (!requests.isEmpty) {
          steps = steps.exec(ApiCall.execute(requests, config))
          priorStream = Option(stream)
        }
      }
    }
    steps
  }

  /**
    * Builds Gatling scenario with http requests
    * @param steps chain of http requests
    * @param scenarioName scenario name to be displayed in the html report
    * @return ScenarioBuilder that can be used to build population
    */
  def buildScenario(
      steps: ChainBuilder,
      scenarioName: String
  ): ScenarioBuilder = {
    scenario(scenarioName).exec(steps)
  }

  /**
    * Builds Gatling population that defines scenario and its injection model
    * @param scn scenario with http requests
    * @param steps injection steps
    * @return PopulationBuilder
    */
  def buildPopulation(
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
        modelsMap.get(step.action) match {
          case Some(value) => value
          case _ =>
            throw new IllegalArgumentException(
              s"Injection model '${step.action}' is not supported by GenericJourney"
            )
        }
      )
      .distinct

    // Verify that all steps belong to the same injection model
    if (models.length > 1) {
      throw new IllegalArgumentException(
        s"'${scn.name}': closed and open models shouldn't be used in the same stage, fix scalabilitySetup:\n${modelsMap.toString()}"
      )
    }
    models.head match {
      case "open" => scn.inject(steps.map(step => getOpenInjectionStep(step)))
      case "closed" =>
        scn.inject(steps.map(step => getClosedInjectionStep(step)))
      case _ =>
        throw new IllegalArgumentException("Unknown step model")
    }
  }
}
