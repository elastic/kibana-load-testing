package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper
import org.mockserver.integration.ClientAndServer
import org.mockserver.stop.Stop.stopQuietly

@TestInstance(Lifecycle.PER_CLASS)
class ConfigTest {
  var kibanaServer: ClientAndServer = null
  var esServer: ClientAndServer = null

  @BeforeAll
  def tearUp(): Unit = {
    kibanaServer = ClientAndServer.startClientAndServer(5620)
    esServer = ClientAndServer.startClientAndServer(9220)
    ServerHelper.mockKibanaStatus(kibanaServer)
    ServerHelper.mockEsStatus(esServer)
  }

  @AfterAll
  def tearDown(): Unit = {
    stopQuietly(kibanaServer)
    stopQuietly(esServer)
  }

  @Test
  def createKibanaConfigTest(): Unit = {
    val config = new KibanaConfiguration(
      Helper.readResourceConfigFile("config/local.conf")
    )
    assertEquals(config.baseUrl, "http://localhost:5620")
    assertTrue(!config.buildVersion.isEmpty)
    assertEquals(config.isSecurityEnabled, true)
    assertEquals(config.loginStatusCode, 200)
    assertEquals(
      config.loginPayload,
      """{"providerType":"basic","providerName":"basic","currentURL":"http://localhost:5620/login","params":{"username":"elastic","password":"changeme"}}"""
    )
    assertEquals(config.deploymentId, None)
  }
}
