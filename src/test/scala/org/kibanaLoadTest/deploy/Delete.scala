package org.kibanaLoadTest.deploy

import org.kibanaLoadTest.helpers.CloudHttpClient

object Delete {
  def main(args: Array[String]): Unit = {
    val deploymentId: Option[String] = Option(
      System.getProperty("deploymentId")
    )

    if (deploymentId.isEmpty) {
      throw new RuntimeException("`deploymentId` is required system property")
    }

    val cloudClient = new CloudHttpClient
    cloudClient.deleteDeployment(deploymentId.get)
  }
}
