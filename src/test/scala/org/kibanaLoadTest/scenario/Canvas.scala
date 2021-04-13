package org.kibanaLoadTest.scenario

import cats.implicits._
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper

import java.util.Calendar

object Canvas {
  def loadWorkpad(
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder = {
    val defaultHeaders =
      headers.combine(Map("Referer" -> s"$baseUrl/app/canvas"))
    val headersWithXsrf = defaultHeaders.combine(
      Map(
        "kbn-xsrf" -> "professionally-crafted-string-of-text"
      )
    )
    exec(
      http("canvas: fns")
        .get("/api/canvas/fns")
        .headers(defaultHeaders)
        .check(status.is(200))
    ).exec(
        http("canvas: load workpads list")
          .get("/api/canvas/workpad/find")
          .queryParam("name", "")
          .queryParam("perPage", "10000")
          .headers(defaultHeaders)
          .check(status.is(200))
          .check(jsonPath("$.workpads[0].id").saveAs("workpadId"))
      )
      .exec(session =>
        session
          .set("startMonth", Helper.getMonthStartDate())
          .set("endMonth", Helper.getMonthStartDate(1))
          .set("currentTime", Helper.getDate(Calendar.DAY_OF_MONTH, 0))
      )
      .exec(
        http("canvas: load workpad")
          .get("/api/canvas/workpad/${workpadId}")
          .headers(headersWithXsrf)
          .check(status.is(200))
      )
      .exec(session =>
        session
          .set("startTime", session("currentTime").as[String])
          .set("endTime", session("currentTime").as[String])
          .set("interval", "1w")
      )
      .exec(
        http("canvas: run query 1")
          .post("/api/timelion/run")
          .body(ElFileBody("data/canvasTimelionPayload.json"))
          .asJson
          .headers(headersWithXsrf)
          .check(status.is(200))
      )
      .exec(
        http("canvas: fns es docs 1")
          .post("/api/canvas/fns")
          .body(ElFileBody("data/fns_esdocs.json"))
          .asJson
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .exec(
        http("canvas: fns point series 1")
          .post("/api/canvas/fns")
          .body(ElFileBody("data/fns_pointseries.json"))
          .asJson
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .exec(session =>
        session
          .set("startTime", session("startMonth").as[String])
          .set("endTime", session("endMonth").as[String])
          .set("interval", "1d")
      )
      .exec(
        http("canvas: run query 2")
          .post("/api/timelion/run")
          .body(ElFileBody("data/canvasTimelionPayload.json"))
          .asJson
          .headers(headersWithXsrf)
          .check(status.is(200))
      )
      .exec(
        http("canvas: fns es docs 2")
          .post("/api/canvas/fns")
          .body(ElFileBody("data/fns_esdocs2.json"))
          .asJson
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .exec(
        http("canvas: fns point series 2")
          .post("/api/canvas/fns")
          .body(ElFileBody("data/fns_pointseries2.json"))
          .asJson
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .exec(
        http("canvas: run query 3")
          .post("/api/timelion/run")
          .body(ElFileBody("data/canvasTimelionPayload.json"))
          .asJson
          .headers(headersWithXsrf)
          .check(status.is(200))
      )
      .exec(
        http("canvas: fns point series 3")
          .post("/api/canvas/fns")
          .body(ElFileBody("data/fns_pointseries3.json"))
          .asJson
          .headers(defaultHeaders)
          .check(status.is(200))
      )
  }
}
