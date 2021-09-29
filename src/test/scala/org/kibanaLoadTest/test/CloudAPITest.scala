package org.kibanaLoadTest.test

import org.junit.jupiter.api.Test
import org.kibanaLoadTest.helpers.{CloudHttpClient, DeploymentInfo, Helper}
import org.junit.jupiter.api.Assertions.{
  assertEquals,
  assertFalse,
  assertNotNull,
  assertTrue
}
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

import java.util

class CloudAPITest {
  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def deploymentTest(): Unit = {
    val stackVersion = "7.14.0"
    val cloudClient = new CloudHttpClient
    val config = Helper.readResourceConfigFile("config/deploy/default.conf")
    val payload = cloudClient.preparePayload(stackVersion, config)
    val deployment = cloudClient.createDeployment(payload)
    assertNotNull(deployment.id, "Deployment id is not defined")
    cloudClient.waitForClusterToStart(deployment)
    val hosts = cloudClient.getPublicUrls(deployment.id)
    assertTrue(
      hosts.get("kibanaUrl").get.startsWith("https://"),
      "Kibana Url is incorrect"
    )
    cloudClient.deleteDeployment(deployment.id)
  }

  @Test
  def waitForClusterToStartTest(): Unit = {
    val deployment =
      DeploymentInfo(
        "fakeIt",
        "user",
        "password",
        List("kibana", "elasticsearch")
      )
    val deploymentId = "fakeIt"
    val timeout = 100
    val interval = 20
    def getFailedStatus(deployment: DeploymentInfo): Map[String, String] = {
      // completely ignore id
      Map(
        "kibana" -> "initializing",
        "elasticsearch" -> "started"
      )
    }

    val cloudClient = new CloudHttpClient
    val isReady = cloudClient.waitForClusterToStart(
      deployment,
      getFailedStatus,
      timeout,
      interval
    )

    assertFalse(isReady)
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def getDeploymentsTest(): Unit = {
    val cloudClient = new CloudHttpClient
    val items = cloudClient.getDeployments
    assertTrue(!items.isEmpty)
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def getLatestAvailableVersionTest(): Unit = {
    val cloudClient = new CloudHttpClient
    val version = cloudClient.getLatestAvailableVersion("7.", "general-purpose")
    assertTrue(version.get.startsWith("7."))
  }
}
