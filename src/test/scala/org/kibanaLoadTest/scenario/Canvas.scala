package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Version

object Canvas {
  def loadWorkpad(
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder = {
    val version = new Version(headers("kbn-version"))
    val fnsPath =
      if (version.isAbove79x) "/api/canvas/fns" else "/api/interpreter/fns"
    exec(
      http("canvas workpads")
        .get("/api/canvas/workpad/find")
        .queryParam("name", "")
        .queryParam("perPage", "10000")
        .headers(headers)
        .header("Referer", baseUrl + "/app/canvas")
        .check(status.is(200))
        .check()
        .check(jsonPath("$.workpads[0].id").saveAs("workpadId"))
    ).exitBlockOnFail {
      doIf(version.isAbove79x) {
        exec(
          http("interpreter demo")
            .get(fnsPath)
            .queryParam("name", "")
            .queryParam("perPage", "10000")
            .headers(headers)
            .header("Referer", baseUrl + "/app/canvas")
            .check(status.is(200))
        ).pause(5)
      }.exec(
          http("load workpad")
            .get("/api/canvas/workpad/${workpadId}")
            .headers(headers)
            .header("Referer", baseUrl + "/app/canvas")
            .header("kbn-xsrf", "professionally-crafted-string-of-text")
            .check(status.is(200))
        )
        .pause(1)
        .exec(
          http("query canvas timelion")
            .post("/api/timelion/run")
            .body(ElFileBody("data/canvasTimelionPayload.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/canvas")
            .header("kbn-xsrf", "professionally-crafted-string-of-text")
            .check(status.is(200))
        )
        .pause(1)
        .exec(
          http("query canvas aggs 1")
            .post(fnsPath)
            .body(ElFileBody("data/canvasInterpreterPayload1.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/canvas")
            .check(status.is(200))
        )
        .pause(1)
        .exec(
          http("query canvas aggs 2")
            .post(fnsPath)
            .body(ElFileBody("data/canvasInterpreterPayload2.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/canvas")
            .check(status.is(200))
        )
    }
  }
}
