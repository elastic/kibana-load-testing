package org.kibanaLoadTest.test

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.kibanaLoadTest.helpers.Helper
import org.kibanaLoadTest.test.mocks.KibanaMockServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response

import java.io.File
import scala.io.Source
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

    val reportDir = Helper.getLastReportPath(
      System.getProperty("user.dir") + File.separator + "results"
    )
    val directory = new Directory(new File(reportDir))

    assertEquals(directory.exists, true, "'results' directory was not created")
    assertTrue(
      directory
        .toString()
        .split(File.separator)
        .last
        .startsWith("genericjourney-"),
      "Gatling report has incorrent folder title"
    )

    // check correct directories were created
    val dirs = directory.dirs.toList
    assertEquals(dirs.length, 2, "Incorrect folders count in Gatling report")
    assertEquals(dirs(0).toString().split(File.separator).last, "js")
    assertEquals(dirs(1).toString().split(File.separator).last, "style")
    // check correct files were created
    val files = directory.files.toList
    assertEquals(files.length, 3, "Incorrect files count in Gatling report")
    assertEquals(files(0).toString().split(File.separator).last, "index.html")
    assertEquals(
      files.last.toString().split(File.separator).last,
      "simulation.log"
    )
    // check index.html file can be parsed
    val REQUESTS_REGEXP = "(?<=var requests = unpack\\(\\[)(.*)(?=\\]\\);)".r
    val RESPONSES_PERCENTILES_REGEXP =
      "(?<=var responsetimepercentilesovertimeokPercentiles = unpack\\(\\[)(.*)(?=\\]\\);)".r
    val source = Source
      .fromFile(reportDir + File.separator + "index.html")
      .getLines
      .mkString

    val minDataPointsCount = 100

    val requestsData = REQUESTS_REGEXP
      .findFirstIn(source)
      .getOrElse("")
      .replaceAll("],\\[", "].[")
      .split("\\.")
    assertTrue(
      requestsData.length > minDataPointsCount,
      s"Report has less than $minDataPointsCount request data points"
    )
    val responsePctData = RESPONSES_PERCENTILES_REGEXP
      .findFirstIn(source)
      .getOrElse("")
      .replaceAll("],\\[", "].[")
      .split("\\.")
    assertTrue(
      responsePctData.length > minDataPointsCount,
      s"Report has less than $minDataPointsCount response data points"
    )
  }
}
