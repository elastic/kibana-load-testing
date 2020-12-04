package org.kibanaLoadTest.scenario

import java.util.Calendar

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper

import scala.concurrent.duration.DurationInt

object Discover {
  private val discoverPayload =
    Helper.loadJsonString("data/discoverPayload.json")
  private def createPayload(startShift: Int, endShift: Int): String = {
    discoverPayload
      .replaceAll(
        "(?<=\"gte\":\")(.*)(?=\",)",
        Helper.getDate(Calendar.DAY_OF_MONTH, startShift)
      )
      .replaceAll(
        "(?<=\"lte\":\")(.*)(?=\",)",
        Helper.getDate(Calendar.DAY_OF_MONTH, endShift)
      )
  }
  private val discoverPayloadQ1 = createPayload(-1, 0)
  private val discoverPayloadQ2 = createPayload(-14, -5)
  private val discoverPayloadQ3 = createPayload(-20, 20)

  def doQuery(baseUrl: String, headers: Map[String, String]): ChainBuilder =
    exec(
      http("discover")
        .post("/internal/search/es")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .body(StringBody(discoverPayloadQ1))
        .asJson
        .check(status.is(200))
    ).pause(5 seconds)
      .exec(
        http("Discover query 2")
          .post("/internal/search/es")
          .headers(headers)
          .header("Referer", baseUrl + "/app/discover")
          .body(StringBody(discoverPayloadQ2))
          .asJson
          .check(status.is(200))
      )
      .pause(5 seconds)
      .exec(
        http("Discover query 3")
          .post("/internal/search/es")
          .headers(headers)
          .header("Referer", baseUrl + "/app/discover")
          .body(StringBody(discoverPayloadQ3))
          .asJson
          .check(status.is(200))
      )
}
