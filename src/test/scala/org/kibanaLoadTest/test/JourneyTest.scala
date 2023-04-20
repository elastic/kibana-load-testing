package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.kibanaLoadTest.simulation.generic.mapping.Journey
import org.kibanaLoadTest.simulation.generic.mapping.JourneyJsonProtocol._

import scala.io.Source.fromFile
import scala.util.Using
import spray.json._

class JourneyTest {
  @Test
  def singleAPIJourneyLoadTest(): Unit = {
    val filePath =
      getClass.getResource("/test/single_api_journey.json").getFile
    val journeyJson =
      Using(fromFile(filePath)) { f =>
        f.getLines().mkString("\n")
      }
    val journey = journeyJson.get.parseJson.convertTo[Journey]

    assertEquals("POST /api/core/capabilities", journey.journeyName)
    assertEquals(journey.streams.length, 1)
    assertEquals(journey.streams(0).requests.length, 1)
    val http = journey.streams(0).requests(0).http
    assertEquals(http.method, "POST")
    assertEquals(http.path, "/api/core/capabilities")
    assertEquals(http.query.get, "?useDefaultCapabilities=true")
    assertTrue(http.body.isDefined)
    assertEquals(http.headers.get("Kbn-Version").get, "")
    assertEquals(http.statusCode, 200)
    assertEquals(http.timeout.get, 10000)
  }
}
