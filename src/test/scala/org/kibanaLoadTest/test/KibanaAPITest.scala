package org.kibanaLoadTest.test

import spray.json.DefaultJsonProtocol.{BooleanJsonFormat, StringJsonFormat}
import spray.json.lenses.JsonLenses._
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.{BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.{Helper, HttpHelper}

@TestInstance(Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "ENV", matches = "local")
class KibanaAPITest {
  private val configPath = "config/local.conf"
  private val soPath = getClass.getResource("/test/so.ndjson").getPath
  private var config: KibanaConfiguration = null
  private var helper: HttpHelper = null

  @BeforeAll
  def initClient: Unit = {
    config = new KibanaConfiguration(
      Helper.readResourceConfigFile(configPath)
    )
    helper = new HttpHelper(config)
  }

  @Test
  def sampleDataTest(): Unit = {
    helper.addSampleData("ecommerce")
  }

  @Test
  def statusCheckTest(): Unit = {
    val statusResponse = helper.getStatus
    val buildNumber = statusResponse
      .extract[String](Symbol("version") / Symbol("number"))
    assertEquals(config.version, buildNumber)
  }

  @Test
  def importSavedObjectsTest(): Unit = {
    val importResponse = helper.importSavedObjects(soPath)
    val isSuccess = importResponse
      .extract[Boolean](Symbol("success"))
    assertEquals(true, isSuccess)
  }

  @Test
  def getESDataTest(): Unit = {
    val responseJson = helper.getElasticSearchData
    val tagline =
      responseJson.hcursor.downField("tagline").as[String].getOrElse("")
    assertEquals("You Know, for Search", tagline)
  }
}
