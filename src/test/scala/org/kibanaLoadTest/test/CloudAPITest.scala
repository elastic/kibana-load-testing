package org.kibanaLoadTest.test

import org.junit.jupiter.api.Test
import org.kibanaLoadTest.helpers.{CloudHttpClient, Helper}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}

class CloudAPITest {

  @Test
  def deploymentTest = {
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
}
