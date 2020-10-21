package org.kibanaLoadTest.scenario

import java.util.Calendar

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper

import scala.concurrent.duration.DurationInt

object Discover {
  private val discoverPayload = Helper.loadJsonString("data/discoverPayload.json")
  private val discoverPayloadQ1 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -1))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 0))
  private val discoverPayloadQ2 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -14))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -5))
  private val discoverPayloadQ3 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -20))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 20))

  def doQuery(baseUrl: String, headers: Map[String, String]) = exec(http("discover")
    .post("/internal/search/es")
    .headers(headers)
    .header("Referer", baseUrl + "/app/discover")
    .body(StringBody(discoverPayloadQ1)).asJson
    .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("Discover query 2")
      .post("/internal/search/es")
      .headers(headers)
      .header("Referer", baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ2)).asJson
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("Discover query 3")
      .post("/internal/search/es")
      .headers(headers)
      .header("Referer", baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ3)).asJson
      .check(status.is(200)))
}
