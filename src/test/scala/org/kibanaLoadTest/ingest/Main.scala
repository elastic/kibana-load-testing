package org.kibanaLoadTest.ingest

import java.io.File
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.{ESClient, GatlingStats, Helper, LogParser}
import org.kibanaLoadTest.helpers.Helper.getReportFolderPaths
import org.slf4j.{Logger, LoggerFactory}
import io.circe._
import io.circe.parser._
import java.nio.file.{Files, Paths}
import scala.collection.parallel.CollectionConverters._
import scala.io.Source

object Main {
  val logger: Logger = LoggerFactory.getLogger("ingest:Main")
  val DATA_INDEX = "gatling-data"
  val GLOBAL_STATS_INDEX = "gatling-stats"
  val SIMULATION_LOG_FILENAME = "simulation.log"
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
    reportFolders.foreach(root => {
      val testRunFilePath = root + File.separator + TEST_RUN_FILENAME
      val simLogFilePath = root + File.separator + SIMULATION_LOG_FILENAME
      val statsFilePath =
        root + File.separator + "js" + File.separator + GLOBAL_STATS_FILENAME
      Array(testRunFilePath, simLogFilePath, statsFilePath).foreach(path => {
        if (!Files.exists(Paths.get(path))) {
          throw new RuntimeException(
            s"Required file '$path' is not found"
          )
        } else {
          logger.info(s"Report found: '$path'")
        }
      })

      val statsJsonString =
        GatlingStats.toJsonString(
          Source.fromFile(statsFilePath).getLines().mkString
        )
      val statsJson = parse(statsJsonString).getOrElse(Json.Null)
      val requests = LogParser.getRequests(simLogFilePath)
      val metaJson = Helper.getMetaJson(testRunFilePath, simLogFilePath)
      // final Json objects to ingest
      val combinedStatsJson = statsJson.deepMerge(metaJson)
      val requestJsonList = requests.par
        .map(request => {
          val requestJson = parse(request.toJsonString).getOrElse(Json.Null)
          val combinedRequestJson = requestJson.deepMerge(metaJson)
          combinedRequestJson
        })
        .toList

      esClient.ingest(GLOBAL_STATS_INDEX, List(combinedStatsJson))
      esClient.ingest(DATA_INDEX, requestJsonList)
    })
  }
}
