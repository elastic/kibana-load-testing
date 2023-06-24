package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.{
  assertDoesNotThrow,
  assertEquals,
  assertNotEquals,
  assertThrows,
  assertTrue
}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.helpers.KbnClient
import org.kibanaLoadTest.test.mocks.KibanaMockServer
import spray.json.lenses.JsonLenses._
import spray.json.DefaultJsonProtocol.{
  BooleanJsonFormat,
  IntJsonFormat,
  StringJsonFormat
}

@TestInstance(Lifecycle.PER_CLASS)
class KibanaClientTest {
  val port = 5601
  val kibanaUrl = s"http://localhost:$port"
  val providerType = "basic"
  val providerName = "basic"
  val username = "elastic"
  val password = "changeme"
  var kbnMock: KibanaMockServer = null
  val kbnVersion = "8.8.8-SNAPSHOT"
  val buildHash = "xyz"
  val buildNumber = 1234
  val buildSnapshot = false

  @BeforeAll
  def tearUp: Unit = {
    kbnMock = new KibanaMockServer(port)
    kbnMock.createKibanaIndexPageCallback(version = kbnVersion)
    kbnMock.createKibanaStatusCallback(
      build_hash = buildHash,
      build_number = buildNumber,
      build_snapshot = buildSnapshot,
      number = kbnVersion
    )
    kbnMock.createSuccessfulLoginCallback()
  }

  @AfterAll
  def tearDown(): Unit = {
    kbnMock.destroy()
  }

  @Test
  def kbngetClientAndConnManagerTest() = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    val closureToTest: Executable = () => client.getClientAndConnectionManager()
    assertDoesNotThrow(closureToTest)
  }

  @Test
  def getKibanaVersionTest(): Unit = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    val serverVersion = client.getVersion()
    assertEquals(kbnVersion, serverVersion)
  }

  @Test
  def failToGetVersionTest(): Unit = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    kbnMock.createBadLoginHtmlPageCallback()
    val getVersionClosure: Executable = () => client.getVersion()
    val exception = assertThrows(classOf[RuntimeException], getVersionClosure)
    assertEquals(
      "Cannot parse kbn-version in login html page",
      exception.getMessage
    )
  }

  @Test
  def sampleDataTest(): Unit = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    kbnMock.createAddSampleDataCallback(dataType = "ecommerce")
    kbnMock.createDeleteSampleDataCallback("ecommerce")

    val loadClosure: Executable = () => client.addSampleData("ecommerce")
    val unloadClosure: Executable = () => client.removeSampleData("ecommerce")
    assertDoesNotThrow(loadClosure, "client.addSampleData throws exception")
    assertDoesNotThrow(
      unloadClosure,
      "client.removeSampleData throws exception"
    )
  }

  @Test
  def generateCookiesTest(): Unit = {
    val cookieCount = 1000
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    val cookieLst = client.generateCookies(cookieCount)
    assertEquals(cookieCount, cookieLst.length)
    assertTrue(cookieLst(0).startsWith("sid=Fe26.2"))
    assertNotEquals(cookieLst(0), cookieLst(1), "cookies must be unique")
  }

  @Test
  def getKibanaStatusTest(): Unit = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )

    val responseString = client.getKibanaStatusInfo()
    assertEquals(
      buildHash,
      responseString.extract[String](Symbol("version") / Symbol("build_hash"))
    )
    assertEquals(
      buildNumber,
      responseString.extract[Int](Symbol("version") / Symbol("build_number"))
    )
    assertEquals(
      buildSnapshot,
      responseString.extract[Boolean](
        Symbol("version") / Symbol("build_snapshot")
      )
    )
    assertEquals(
      kbnVersion,
      responseString.extract[String](Symbol("version") / Symbol("number"))
    )
  }

  @Test
  def failToLoginTest(): Unit = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    kbnMock.createForbiddenLoginCallback()
    val closure: Executable = () => client.generateCookies(1)
    val exception = assertThrows(classOf[RuntimeException], closure)
    assertEquals(
      "Failed to login: HTTP/1.1 403 Forbidden",
      exception.getMessage
    )
  }

  @Test
  def responseWithoutSetCookieHeaderTest(): Unit = {
    val client = new KbnClient(
      kibanaUrl,
      username,
      password,
      providerName,
      providerType
    )
    kbnMock.createNoCookieInHeadersLoginCallback()
    val closure: Executable = () => client.generateCookies(1)
    val exception = assertThrows(classOf[RuntimeException], closure)
    assertEquals("Response has no 'set-cookie' header", exception.getMessage)
  }
}
