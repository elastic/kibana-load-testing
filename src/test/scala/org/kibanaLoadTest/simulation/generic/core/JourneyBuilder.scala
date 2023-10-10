package org.kibanaLoadTest.simulation.generic.core

import io.gatling.core.Predef._
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.structure.{ChainBuilder, PopulationBuilder, ScenarioBuilder}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.simulation.generic.mapping
import org.kibanaLoadTest.simulation.generic.mapping.{RequestStream, Step}
import org.slf4j.{Logger, LoggerFactory}

import java.util.concurrent.TimeUnit

object JourneyBuilder {
  private val logger: Logger = LoggerFactory.getLogger("Scenario")
  private val modelsMap = Map(
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
      steps = steps.exec(ApiCall.execute(streams.head.requests, config))
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
        val excludeUrls = Array("/api/status", "/api/saved_objects/_import")
        val requests =
          stream.requests.filter(req =>
            !excludeUrls.contains(req.getRequestUrl())
          )

        if (requests.nonEmpty) {
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

  private def getClosedInjectionStep(step: Step): ClosedInjectionStep = {
    logger.info(s"Closed model: building ${step.toString}")
    step.action match {
      case "constantConcurrentUsers" =>
        step.userCount match {
          case Some(count) =>
            step.duration match {
              case Some(duration) =>
                constantConcurrentUsers(count).during(getDuration(duration))
              case None =>
                throw new RuntimeException(
                  "'constantConcurrentUsers' step requires 'duration' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'constantConcurrentUsers' step requires 'userCount' prop"
            )
        }
      case "rampConcurrentUsers" =>
        step.minUsersCount match {
          case Some(min) =>
            step.maxUsersCount match {
              case Some(max) =>
                step.duration match {
                  case Some(duration) =>
                    rampConcurrentUsers(min)
                      .to(max)
                      .during(getDuration(duration))
                  case None =>
                    throw new RuntimeException(
                      "'rampConcurrentUsers' step requires 'duration' prop"
                    )
                }
              case None =>
                throw new RuntimeException(
                  "'rampConcurrentUsers' step requires 'maxUsersCount' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'rampConcurrentUsers' step requires 'minUsersCount' prop"
            )
        }
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
        step.userCount match {
          case Some(count) => atOnceUsers(count)
          case None =>
            throw new RuntimeException(
              "'atOnceUsers' step requires 'userCount' prop"
            )
        }
      case "rampUsers" =>
        step.userCount match {
          case Some(count) =>
            step.duration match {
              case Some(duration) =>
                rampUsers(count).during(getDuration(duration))
              case None =>
                throw new RuntimeException(
                  "'rampUsers' step requires 'duration' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'rampUsers' step requires 'userCount' prop"
            )
        }
      case "constantUsersPerSec" =>
        step.userCount match {
          case Some(count) =>
            step.duration match {
              case Some(duration) =>
                constantUsersPerSec(count.toDouble)
                  .during(getDuration(duration))
              case None =>
                throw new RuntimeException(
                  "'constantUsersPerSec' step requires 'duration' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'constantUsersPerSec' step requires 'userCount' prop"
            )
        }
      case "stressPeakUsers" =>
        step.userCount match {
          case Some(count) =>
            step.duration match {
              case Some(duration) =>
                stressPeakUsers(count).during(getDuration(duration))
              case None =>
                throw new RuntimeException(
                  "'stressPeakUsers' step requires 'duration' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'stressPeakUsers' step requires 'userCount' prop"
            )
        }
      case "rampUsersPerSec" =>
        step.minUsersCount match {
          case Some(min) =>
            step.maxUsersCount match {
              case Some(max) =>
                step.duration match {
                  case Some(duration) =>
                    rampUsersPerSec(min).to(max).during(getDuration(duration))
                  case None =>
                    throw new RuntimeException(
                      "'rampUsersPerSec' step requires 'duration' prop"
                    )
                }
              case None =>
                throw new RuntimeException(
                  "'rampUsersPerSec' step requires 'maxUsersCount' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'rampUsersPerSec' step requires 'minUsersCount' prop"
            )
        }
      case "incrementUsersPerSec" =>
        step.userCount match {
          case Some(count) =>
            step.times match {
              case Some(times) =>
                step.duration match {
                  case Some(duration) =>
                    incrementUsersPerSec(count.toDouble)
                      .times(times)
                      .eachLevelLasting(getDuration(duration))
                      .separatedByRampsLasting(getDuration(duration))
                      .startingFrom(count.toDouble)
                  case None =>
                    throw new RuntimeException(
                      "'rampUsersPerSec' step requires 'duration' prop"
                    )
                }
              case None =>
                throw new RuntimeException(
                  "'rampUsersPerSec' step requires 'times' prop"
                )
            }
          case None =>
            throw new RuntimeException(
              "'rampUsersPerSec' step requires 'userCount' prop"
            )
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Invalid open model: ${step.action}"
        )
    }
  }

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
          s"Invalid duration format: $duration"
        )
    }
  }
}
