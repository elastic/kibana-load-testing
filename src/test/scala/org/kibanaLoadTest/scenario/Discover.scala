package org.kibanaLoadTest.scenario

import java.util.Calendar

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper

object Discover {
  private val discoverPayload =
    Helper.loadJsonString("data/bsearchPayload.json")
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
  val discoverPayloadQ1 = createPayload(-1, 0)
  val discoverPayloadQ2 = createPayload(-14, -5)
  val discoverPayloadQ3 = createPayload(-20, 20)

  def doQuery(
      baseUrl: String,
      headers: Map[String, String],
      payload: String
  ): ChainBuilder =
    exec(
      http("Discover query")
        .post("/internal/search/es")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .body(StringBody(payload))
        .asJson
        .check(status.is(200))
    )

  def doQueries(
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder =
    exec(
      http("discover")
        .post("/internal/search/es")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .body(StringBody(discoverPayloadQ1))
        .asJson
        .check(status.is(200))
    ).pause(5)
      .exec(
        http("Discover query 2")
          .post("/internal/search/es")
          .headers(headers)
          .header("Referer", baseUrl + "/app/discover")
          .body(StringBody(discoverPayloadQ2))
          .asJson
          .check(status.is(200))
      )
      .pause(5)
      .exec(
        http("Discover query 3")
          .post("/internal/search/es")
          .headers(headers)
          .header("Referer", baseUrl + "/app/discover")
          .body(StringBody(discoverPayloadQ3))
          .asJson
          .check(status.is(200))
      )

  def load(
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder =
    exec(
      http("Load index patterns")
        .get("/api/saved_objects/_find")
        .queryParam("fields", "title")
        .queryParam("per_page", "10000")
        .queryParam("type", "index-pattern")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .asJson
        .check(status.is(200))
    )
    .pause(5)
    .exec(
      http("Load index pattern fields")
        .get("/api/index_patterns/_fields_for_wildcard")
        .queryParam("pattern", "kibana_sample_data_ecommerce")
        .queryParam("meta_fields", "_source")
        .queryParam("meta_fields", "_id")
        .queryParam("meta_fields", "_type")
        .queryParam("meta_fields", "_index")
        .queryParam("meta_fields", "_score")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .asJson
        .check(status.is(200))
    )
    .pause(5)
    .exec(
      http("Discover query 1")
        .post("/internal/bsearch")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .body(StringBody(discoverPayloadQ1))
        .asJson
        .check(status.is(200))
    )
    .pause(5)
    .exec(
      http("Discover query 2")
        .post("/internal/bsearch")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .body(StringBody(discoverPayloadQ2))
        .asJson
        .check(status.is(200))
    )
    .pause(5)
    .exec(
      http("Discover query 3")
        .post("/internal/bsearch")
        .headers(headers)
        .header("Referer", baseUrl + "/app/discover")
        .body(StringBody(discoverPayloadQ3))
        .asJson
        .check(status.is(200))
    )
}
