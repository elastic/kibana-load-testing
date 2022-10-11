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
import org.junit.jupiter.api.{BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{HttpHelper, KbnClient}

import java.nio.file.Paths

@TestInstance(Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
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

  @BeforeAll
  def init: Unit = {
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

  @Test
  def kbngetClientAndConnManagerTest() = {
    val client = new KbnClient(config)
    val closureToTest: Executable = () => client.getClientAndConnectionManager()
    assertDoesNotThrow(closureToTest)
  }

  @Test
  def kbnClientLoadUloadTest() = {
    val client = new KbnClient(config)
    val loadClosure: Executable = () => client.load(savedObjectPath)
    val unloadClosure: Executable = () => client.unload(savedObjectPath)
    assertDoesNotThrow(loadClosure, "client.load throws exception")
    assertDoesNotThrow(unloadClosure, "client.unload throws exception")
  }

  @Test
  def sampleDataTest(): Unit = {
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
