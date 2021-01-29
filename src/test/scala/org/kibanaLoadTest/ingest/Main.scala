package org.kibanaLoadTest.ingest

import java.io.File
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.kibanaLoadTest.ESConfiguration
import org.kibanaLoadTest.helpers.ESWrapper
import org.kibanaLoadTest.helpers.Helper.{getReportFolderPaths, getTargetPath}

object Main {
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

    val esClient = new ESWrapper(esConfig)
    val lastDeploymentFilePath =
      getTargetPath + File.separator + "lastDeployment.txt"
    val simulationFiles =
      getReportFolderPaths.map(_ + File.separator + "simulation.log")
    simulationFiles.foreach(_ => esClient.ingest(_, lastDeploymentFilePath))
  }
}
