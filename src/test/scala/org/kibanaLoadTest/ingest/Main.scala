package org.kibanaLoadTest.ingest

import java.io.File
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.{ESClient, Helper}
import org.kibanaLoadTest.helpers.Helper.getReportFolderPaths
import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.{Files, Paths}

object Main {
  val logger: Logger = LoggerFactory.getLogger("ingest:Main")
  val USERS_INDEX = "gatling-users"
  val DATA_INDEX = "gatling-data"
  val GLOBAL_STATS_INDEX = "gatling-stats"
  val SIMULATION_LOG_FILENAME = "simulation.log"
  val RESPONSE_LOG_FILENAME = "response.log"
  val GLOBAL_STATS_FILENAME = "global_stats.json"
  val TEST_RUN_FILENAME = "testRun.txt"

  def main(args: Array[String]): Unit = {
    val hostValue = System.getenv("HOST_FROM_VAULT")
    val host =
      if (hostValue.startsWith("http")) hostValue else "https://" + hostValue
    val username = System.getenv("USER_FROM_VAULT")
    val password = System.getenv("PASS_FROM_VAULT")

    val esConfig = new ESConfiguration(
      ConfigFactory.load
        .withValue("host", ConfigValueFactory.fromAnyRef(host))
        .withValue("username", ConfigValueFactory.fromAnyRef(username))
        .withValue("password", ConfigValueFactory.fromAnyRef(password))
    )

    val esClient = new ESClient(esConfig)
    val reportFolders = getReportFolderPaths

    logger.info(s"Found ${reportFolders.length} Gatling reports")
    var i = 0
    val reportsCount = reportFolders.size
    while (i < reportsCount) {
      val testRunFilePath =
        reportFolders(i) + File.separator + TEST_RUN_FILENAME
      val simLogFilePath =
        reportFolders(i) + File.separator + SIMULATION_LOG_FILENAME
      val responseFilePath =
        reportFolders(i) + File.separator + RESPONSE_LOG_FILENAME
      val statsFilePath =
        reportFolders(
          i
        ) + File.separator + "js" + File.separator + GLOBAL_STATS_FILENAME
      Array(testRunFilePath, simLogFilePath, responseFilePath, statsFilePath)
        .foreach(path => {
          if (!Files.exists(Paths.get(path))) {
            esClient.Instance.closeConnection()
            throw new RuntimeException(
              s"Required file '$path' is not found"
            )
          } else {
            logger.info(s"Report found: '$path'")
          }
        })

      val (requestsArray, concurrentUsersArray, combinedStatsArray) =
        Helper.prepareDocsForIngestion(
          statsFilePath,
          simLogFilePath,
          responseFilePath,
          testRunFilePath
        )

      esClient.Instance.bulk(GLOBAL_STATS_INDEX, combinedStatsArray)
      esClient.Instance.bulk(DATA_INDEX, requestsArray)
      esClient.Instance.bulk(USERS_INDEX, concurrentUsersArray)

      i += 1
    }

    esClient.Instance.closeConnection()
  }
}
