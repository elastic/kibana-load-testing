package org.kibanaLoadTest.deploy

import org.kibanaLoadTest.helpers.{CloudEnv, CloudHttpClient}
import org.slf4j.{Logger, LoggerFactory}

object DeleteAll {
  object Scope extends Enumeration {
    val ALL = Value("all")
    val TEST = Value("test")
  }
  val logger: Logger = LoggerFactory.getLogger("deploy:DeleteAll")

  def main(args: Array[String]): Unit = {
    val scope = Scope.withName(System.getProperty("scope", "test"))
    val env = CloudEnv.withName(System.getProperty("env", "staging"))
    val cleanItems = collection.mutable.Map[String, String]()

    val cloudClient = new CloudHttpClient(env)
    val deployments = cloudClient.getDeployments
    logger.info(
      s"Found ${deployments.size} running deployments on $env environment"
    )
    if (deployments.isEmpty) {
      return
    }
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
    if (cleanItems.nonEmpty) {
      cleanItems.foreach {
        case (_, id) =>
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
