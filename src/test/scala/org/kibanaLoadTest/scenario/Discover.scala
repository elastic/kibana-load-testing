package org.kibanaLoadTest.scenario

import java.util.Calendar
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper

object Discover {
  def doQuery(
      name: String,
      baseUrl: String,
      headers: Map[String, String],
      startTime: String,
      endTime: String,
      interval: String
  ): ChainBuilder = {
    val Array(intervalName, intervalValue) = interval.split(":")
    exec(session =>
      session
        .set("sessionId", Helper.generateUUID)
        .set("startTime", startTime)
        .set("endTime", endTime)
        .set("intervalName", intervalName)
        .set("intervalValue", intervalValue)
    ).exec(
        http(s"Discover query $name")
          .post("/internal/bsearch")
          .headers(headers)
          .header("Referer", baseUrl + "/app/discover")
          .body(ElFileBody("data/discover/bsearch.json"))
          .asJson
          .check(status.is(200).saveAs("status"))
          .check(jsonPath("$.result.id").find.saveAs("requestId"))
          .check(jsonPath("$.result.isPartial").find.saveAs("isPartial"))
      )
      .exitHereIfFailed
      // First response might be “partial”. Then we continue to fetch for the results
      // using the request id returned from the first response
      .doWhile(session =>
        session("status").as[Int] == 200
          && session("isPartial").as[Boolean]
      ) {
        exec(
          http(s"Discover query (fetch by id) $name")
            .post("/internal/bsearch")
            .headers(headers)
            .header("Referer", baseUrl + "/app/discover")
            .body(ElFileBody("data/discover/bsearchRequestId.json"))
            .asJson
            .check(status.is(200).saveAs("status"))
            .check(jsonPath("$.result.isPartial").saveAs("isPartial"))
        )
      }
  }

  def load(
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder = {
    val startTime = Helper.getDate(Calendar.MINUTE, -15)
    val endTime = Helper.getDate(Calendar.DAY_OF_MONTH, 0)
    exec(session => session.set("preference", System.currentTimeMillis()))
      .exec(
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
      .exitBlockOnFail {
        exec(
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
        ).exec(
          doQuery(
            "default",
            baseUrl,
            headers,
            startTime,
            endTime,
            "fixed_interval:30s"
          )
        )
      }
  }

  def do2ExtraQueries(
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder =
    exec(
      doQuery(
        "2",
        baseUrl,
        headers,
        Helper.getDate(Calendar.DAY_OF_MONTH, -5),
        Helper.getDate(Calendar.DAY_OF_MONTH, 0),
        "fixed_interval:3h"
      )
    ).pause(10)
      .exec(
        doQuery(
          "3",
          baseUrl,
          headers,
          Helper.getDate(Calendar.DAY_OF_MONTH, -30),
          Helper.getDate(Calendar.DAY_OF_MONTH, 0),
          "calendar_interval:1d"
        )
      )
}
