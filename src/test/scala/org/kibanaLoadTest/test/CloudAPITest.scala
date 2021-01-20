package org.kibanaLoadTest.test

import org.junit.jupiter.api.Test
import org.kibanaLoadTest.helpers.{CloudHttpClient, Helper}
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

class CloudAPITest {
  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def deploymentTest(): Unit = {
    val stackVersion = "7.10.0"
    val cloudClient = new CloudHttpClient
    val config = Helper.readResourceConfigFile("config/deploy/default.conf")
    val payload = cloudClient.preparePayload(stackVersion, config)
    val metadata = cloudClient.createDeployment(payload)
    assertEquals(metadata.size, 3, "metadata size is incorrect")
    cloudClient.waitForClusterToStart(metadata("deploymentId"))
    val host = cloudClient.getKibanaUrl(metadata("deploymentId"))
    assertTrue(host.startsWith("https://"), "Kibana Url is incorrect")
    cloudClient.deleteDeployment(metadata("deploymentId"))
  }

  @Test
  def waitForClusterToStartTest(): Unit = {
    val deploymentId = "fakeIt"
    val timeout = 100
    val interval = 20
    def getFakeStatus(id: String): Map[String, String] = {
      // completely ignore id
      Map(
        "kibana" -> "initializing",
        "elasticsearch" -> "started"
      )
    }

    val exceptionThatWasThrown = assertThrows(
      classOf[RuntimeException],
      () => {
        val cloudClient = new CloudHttpClient
        cloudClient.waitForClusterToStart(
          deploymentId,
          getFakeStatus,
          timeout,
          interval
        )
      }
    )

    assertEquals(
      s"Deployment $deploymentId was not ready after $timeout ms",
      exceptionThatWasThrown.getMessage
    )
  }

}
