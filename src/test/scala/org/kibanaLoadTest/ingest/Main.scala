package org.kibanaLoadTest.ingest

import java.io.File

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.{ESWrapper}
import org.kibanaLoadTest.helpers.Helper.{getLastReportPath, getTargetPath}

object Main {
  def main(args: Array[String]): Unit = {
    val host = "https://" + System.getenv("HOST_FROM_VAULT")
    val username = System.getenv("USER_FROM_VAULT")
    val password = System.getenv("PASS_FROM_VAULT")

    val esConfig = new ESConfiguration(
      ConfigFactory.load
        .withValue("host", ConfigValueFactory.fromAnyRef(host))
        .withValue("username", ConfigValueFactory.fromAnyRef(username))
        .withValue("password", ConfigValueFactory.fromAnyRef(password))
    )

    val esClient = new ESWrapper(esConfig)
    val logFilePath = getLastReportPath() + File.separator + "simulation.log"
    val lastDeploymentFilePath =
      getTargetPath() + File.separator + "lastDeployment.txt"
    esClient.ingest(logFilePath, lastDeploymentFilePath)
  }
}
