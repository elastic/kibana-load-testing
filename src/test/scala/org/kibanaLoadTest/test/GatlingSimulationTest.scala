package org.kibanaLoadTest.test

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.test.mocks.KibanaMockServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

import java.io.File
import scala.reflect.io.Directory

@TestInstance(Lifecycle.PER_CLASS)
class GatlingSimulationTest {
  val port = 5620
  var kbnMock: KibanaMockServer = null

  @BeforeAll
  def tearUp: Unit = {
    kbnMock = new KibanaMockServer(port)
    kbnMock.createKibanaIndexPageCallback()
    kbnMock.createKibanaStatusCallback()
    kbnMock.createSuccessfulLoginCallback()
  }

  @AfterAll
  def tearDown(): Unit = {
    kbnMock.destroy()
  }

  @Test
  def runGenericJourneySimulationTest(): Unit = {
    kbnMock.addApiMockCallback(
      request
        .withPath("/api/core/capabilities")
        .withMethod("POST")
        .withQueryStringParameter("useDefaultCapabilities", "true"),
      response()
        .withStatusCode(200)
    )
    val filePath =
      getClass.getResource("/test/single_api_journey.json").getFile
    System.setProperty("journeyPath", filePath)
    val props = new GatlingPropertiesBuilder()
      .simulationClass("org.kibanaLoadTest.simulation.generic.GenericJourney")
    Gatling.fromMap(props.build)
    val directory = new Directory(
      new File(System.getProperty("user.dir") + File.separator + "results")
    )
    assertEquals(directory.exists, true, "'results' directory was not created")
  }
}
