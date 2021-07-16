package org.kibanaLoadTest.scenario

import java.util.Calendar
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.structure.ChainBuilder
import org.kibanaLoadTest.helpers.Helper

object Visualize {
  def load(
      vizType: String,
      id: String,
      queryJson: String,
      baseUrl: String,
      headers: Map[String, String]
  ): ChainBuilder = {
    exec(session =>
      session
        .set("vizId", id)
        .set("startTime", Helper.getDate(Calendar.DAY_OF_MONTH, -7))
        .set("endTime", Helper.getDate(Calendar.DAY_OF_MONTH, 0))
        .set("preference", System.currentTimeMillis())
    ).exec(
        http("saved_objects/_bulk_get")
          .post("/api/saved_objects/_bulk_get")
          .headers(headers)
          .header("Referer", baseUrl + "/app/visualize")
          .body(
            StringBody("[{\"id\":\"${vizId}\",\"type\":\"visualization\"}]")
          )
          .check(status.is(200))
          .check(
            jsonPath("$.saved_objects[0].references[0].id").find
              .saveAs("indexPatternId")
          )
      )
      .pause(1)
      .doIfOrElse(vizType == "tsvb") {
        exec(
          http("metrics/vis/data")
            .post("/api/metrics/vis/data")
            .headers(headers)
            .header("Referer", baseUrl + "/app/visualize")
            .body(ElFileBody(queryJson))
            .asJson
            .check(status.is(200))
        )
      } {
        exec(
          http("bsearch")
            .post("/internal/bsearch")
            .headers(headers)
            .header("Referer", baseUrl + "/app/visualize")
            .body(ElFileBody(queryJson))
            .asJson
            .check(status.is(200))
        )
      }
  }
}
