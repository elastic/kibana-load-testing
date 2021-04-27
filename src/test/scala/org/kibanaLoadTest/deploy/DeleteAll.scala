package org.kibanaLoadTest.deploy

import org.kibanaLoadTest.helpers.CloudHttpClient
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable.{AbstractMap, SeqMap, SortedMap}

object DeleteAll {
  object Scope extends Enumeration {
    val ALL = Value("all")
    val TEST = Value("test")
  }
  val logger: Logger = LoggerFactory.getLogger("deploy:DeleteAll")

  def main(args: Array[String]): Unit = {
    val scope = Scope.withName(System.getProperty("scope", "test"))
    var cleanItems = collection.mutable.Map[String, String]()

    val cloudClient = new CloudHttpClient
    val deployments = cloudClient.getDeployments
    logger.info(s"Found ${deployments.size} running deployments")
    if (scope == Scope.ALL) {
      logger.info(s"Deleting all deployments")
      cleanItems.addAll(deployments)
    } else {
      logger.info(s"Deleting only 'load-testing' deployments")
      deployments.foreach {
        case (name, id) => {
          if (name.startsWith("load-testing")) {
            cleanItems += name -> id
          }
        }
      }
    }

    logger.info(s"Got ${cleanItems.size} deployments to delete")
    if (!cleanItems.isEmpty) {
      cleanItems.foreach {
        case (name, id) =>
          cloudClient.deleteDeployment(id)
          Thread.sleep(2 * 1000)
      }
      // wait a bit for deployments to be deleted
      logger.info(s"Waiting...")
      Thread.sleep(20 * 1000)
      // checking running deployments again
      val deploymentsAfter = cloudClient.getDeployments
      logger.info(s"Found ${deploymentsAfter.size} deployments after cleanup")
      if (deploymentsAfter.nonEmpty) {
        deploymentsAfter.foreach {
          case (name, id) => logger.info(s"name: $name, id: $id")
        }
      }
    }
  }
}
