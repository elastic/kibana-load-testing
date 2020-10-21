package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

object Canvas {
  def loadWorkpad(baseUrl: String, headers: Map[String, String]) = exec(http("canvas workpads")
    .get("/api/canvas/workpad/find")
    .queryParam("name", "")
    .queryParam("perPage", "10000")
    .headers(headers)
    .header("Referer", baseUrl + "/app/canvas")
    .check(status.is(200))
    .check()
    .check(jsonPath("$.workpads[0].id").saveAs("workpadId")))
    .exitBlockOnFail {
      exec(http("interpreter demo")
        .get("/api/interpreter/fns")
        .queryParam("name", "")
        .queryParam("perPage", "10000")
        .headers(headers)
        .header("Referer", baseUrl + "/app/canvas")
        .check(status.is(200)))
        .pause(5 seconds)
        .exec(http("load workpad")
          .get("/api/canvas/workpad/${workpadId}")
          .headers(headers)
          .header("Referer", baseUrl + "/app/canvas")
          .header("kbn-xsrf", "professionally-crafted-string-of-text")
          .check(status.is(200)))
        .pause(1 seconds)
        .exec(http("query canvas timelion")
          .post("/api/timelion/run")
          .body(ElFileBody("data/canvasTimelionPayload.json")).asJson
          .headers(headers)
          .header("Referer", baseUrl + "/app/canvas")
          .header("kbn-xsrf", "professionally-crafted-string-of-text")
          .check(status.is(200)))
        .pause(1 seconds)
        .exec(http("query canvas aggs 1")
          .post("/api/interpreter/fns")
          .body(ElFileBody("data/canvasInterpreterPayload1.json")).asJson
          .headers(headers)
          .header("Referer", baseUrl + "/app/canvas")
          .check(status.is(200)))
        .pause(1 seconds)
        .exec(http("query canvas aggs 2")
          .post("/api/interpreter/fns")
          .body(ElFileBody("data/canvasInterpreterPayload2.json")).asJson
          .headers(headers)
          .header("Referer", baseUrl + "/app/canvas")
          .check(status.is(200)))
    }
}
