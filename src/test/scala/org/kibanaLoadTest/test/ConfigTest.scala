package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper
import org.kibanaLoadTest.test.mocks.{ESMockServer, KibanaMockServer}

@TestInstance(Lifecycle.PER_CLASS)
class ConfigTest {
  var kbnMock: KibanaMockServer = null
  var esMock: ESMockServer = null

  @BeforeAll
  def tearUp(): Unit = {
    kbnMock = new KibanaMockServer(5620)
    kbnMock.createKibanaStatusCallback()

    esMock = new ESMockServer(9220)
    esMock.createStatusCallback()
  }

  @AfterAll
  def tearDown(): Unit = {
    kbnMock.destroy()
    esMock.destroy()
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
