package org.kibanaLoadTest.deploy

import org.kibanaLoadTest.helpers.{CloudEnv, CloudHttpClient}
import org.slf4j.{Logger, LoggerFactory}
import scala.collection.mutable.ListBuffer

object DeleteAll {
  object Scope extends Enumeration {
    val ALL: Scope.Value = Value("all")
    val TEST: Scope.Value = Value("test")
  }
  val logger: Logger = LoggerFactory.getLogger("deploy:DeleteAll")
  val timeout: Long = Integer.getInteger("timeout", 2 * 1000).longValue()

  def main(args: Array[String]): Unit = {
    val scope = Scope.withName(System.getProperty("scope", "test"))
    val env = CloudEnv.withName(System.getProperty("env", "staging"))
    val cleanItems = ListBuffer[String]()

    val cloudClient = new CloudHttpClient(env)
    val deployments = cloudClient.getDeployments
    logger.info(
      s"Found ${deployments.size} running deployments on $env environment"
    )
    if (deployments.isEmpty) {
      logger.info("Nothing to clean")
      return
    } else {
      deployments.foreach {
        case (id, name) => logger.info(s"id: $id, name: $name")
      }
    }

    if (scope == Scope.ALL) {
      logger.info(s"Deleting all running deployments")
      cleanItems.addAll(deployments.keys)
    } else {
      logger.info(s"Deleting only 'load-testing' deployments")
      deployments.foreach {
        case (id, name) =>
          if (name.startsWith("load-testing")) {
            cleanItems.addOne(id)
          }
      }
    }

    logger.info(s"Got ${cleanItems.size} deployments to delete")
    if (cleanItems.nonEmpty) {
      cleanItems.foreach(id => {
        cloudClient.deleteDeployment(id)
        Thread.sleep(timeout)
      })
      // wait a bit for deployments to be deleted
      logger.info(s"Waiting...")
      Thread.sleep(10 * timeout)
      // checking running deployments again
      val deploymentsAfter = cloudClient.getDeployments
      logger.info(s"Found ${deploymentsAfter.size} deployments after cleanup")
      if (deploymentsAfter.nonEmpty) {
        deploymentsAfter.foreach {
          case (id, name) => logger.info(s"id: $id, name: $name")
        }
      }
    }
  }
}
