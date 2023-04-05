package org.kibanaLoadTest.test

import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Assertions.{
  assertDoesNotThrow,
  assertEquals,
  assertNotEquals,
  assertTrue
}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{HttpHelper, KbnClient}
import org.mockserver.integration.ClientAndServer
import org.mockserver.stop.Stop.stopQuietly

import java.nio.file.Paths

@TestInstance(Lifecycle.PER_CLASS)
class KibanaAPITest {
  private val kibanaHost =
    sys.env.getOrElse("KIBANA_HOST", "http://localhost:5620")
  private val esHost = sys.env.getOrElse("ES_URL", "http://localhost:9220")
  private val providerType = sys.env.getOrElse("AUTH_PROVIDER_TYPE", "basic")
  private val providerName = sys.env.getOrElse("AUTH_PROVIDER_NAME", "basic")
  private val username = sys.env.getOrElse("AUTH_LOGIN", "elastic")
  private val password = sys.env.getOrElse("AUTH_PASSWORD", "changeme")
  private val savedObjectPath =
    Paths.get(getClass.getResource("/test/so.json").getPath)
  private var config: KibanaConfiguration = null
  private var helper: HttpHelper = null
  var kibanaServer: ClientAndServer = null
  var esServer: ClientAndServer = null

  @BeforeAll
  def tearUp: Unit = {
    kibanaServer = ClientAndServer.startClientAndServer(5620)
    esServer = ClientAndServer.startClientAndServer(9220)
    ServerHelper.mockKibanaStatus(kibanaServer)
    ServerHelper.mockKibanaLogin(kibanaServer)
    ServerHelper.mockEsStatus(esServer)
    config = new KibanaConfiguration(
      kibanaHost,
      esHost,
      username,
      password,
      providerType,
      providerName
    )
    helper = new HttpHelper(config)
  }

  @AfterAll
  def tearDown(): Unit = {
    stopQuietly(kibanaServer)
    stopQuietly(esServer)
  }

  @Test
  def kbngetClientAndConnManagerTest() = {
    val client = new KbnClient(config)
    val closureToTest: Executable = () => client.getClientAndConnectionManager()
    assertDoesNotThrow(closureToTest)
  }

  /**
    * This test should be run locally against real Kibana instance
    */
  @Test
  @EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
  def kbnClientLoadUloadTest() = {
    val client = new KbnClient(config)
    val loadClosure: Executable = () => client.load(savedObjectPath)
    val unloadClosure: Executable = () => client.unload(savedObjectPath)
    assertDoesNotThrow(loadClosure, "client.load throws exception")
    assertDoesNotThrow(unloadClosure, "client.unload throws exception")
  }

  @Test
  def sampleDataTest(): Unit = {
    ServerHelper.mockKibanaSampleData(
      kibanaServer,
      dataType = "ecommerce",
      config.buildVersion
    )
    val loadClosure: Executable = () => helper.addSampleData("ecommerce")
    val unloadClosure: Executable = () => helper.removeSampleData("ecommerce")
    assertDoesNotThrow(loadClosure, "helper.addSampleData throws exception")
    assertDoesNotThrow(
      unloadClosure,
      "helper.removeSampleData throws exception"
    )
  }

  @Test
  def loginIfNeededTest(): Unit = {
    val httpClient = HttpClientBuilder.create.build
    val loginClosure: Executable = () => helper.loginIfNeeded(httpClient)
    assertDoesNotThrow(loginClosure, "helper.loginIfNeeded throws exception")
  }

  @Test
  def generateCookiesTest(): Unit = {
    val cookieCount = 1000
    val client = new KbnClient(config)
    val cookieLst = client.generateCookies(cookieCount)
    assertEquals(cookieCount, cookieLst.length)
    assertTrue(cookieLst(0).startsWith("sid=Fe26.2"))
    assertNotEquals(cookieLst(0), cookieLst(1), "cookies must be unique")
  }
}
