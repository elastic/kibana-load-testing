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
        .set("sessionId", Helper.generateUUID)
    ).exec(
        http("bulk_resolve: visualization")
          .post("/api/saved_objects/_bulk_resolve")
          .headers(headers)
          .header("Referer", baseUrl + "/app/visualize")
          .body(
            StringBody("[{\"id\":\"${vizId}\",\"type\":\"visualization\"}]")
          )
          .check(status.is(200))
          .check(
            jsonPath("$..references[0].id").find
              .saveAs("indexPatternId")
          )
      )
      .pause(1)
      .exec(
        http("bulk_resolve: index-pattern")
          .post("/api/saved_objects/_bulk_resolve")
          .headers(headers)
          .header("Referer", baseUrl + "/app/visualize")
          .body(
            StringBody(
              "[{\"id\":\"${indexPatternId}\",\"type\":\"index-pattern\"}]"
            )
          )
          .check(status.is(200))
      )
      .pause(1)
      .exec(
        http("query index pattern meta fields")
          .get("/api/index_patterns/_fields_for_wildcard")
          .queryParam("pattern", "kibana_sample_data_ecommerce")
          .queryParam("meta_fields", "_source")
          .queryParam("meta_fields", "_id")
          .queryParam("meta_fields", "_type")
          .queryParam("meta_fields", "_index")
          .queryParam("meta_fields", "_score")
          .headers(headers)
          .header("Referer", baseUrl + "/app/visualize")
          .check(status.is(200))
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
        group("bsearch") {
          exec(session => session.set("bsearchCounter", 0))
            .exec(
              http("first call")
                .post("/internal/bsearch")
                .headers(headers)
                .header("Referer", baseUrl + "/app/visualize")
                .body(ElFileBody(queryJson))
                .asJson
                .check(status.is(200).saveAs("status"))
                .check(jsonPath("$.result.isPartial").find.saveAs("isPartial"))
                .check(jsonPath("$.result.id").find.saveAs("requestId"))
            )
            .exitHereIfFailed
            // First response might be “partial”. Then we continue to fetch for the results
            // using the requestId returned from the first response
            .asLongAs(session =>
              session("status").as[Int] == 200
                && session("isPartial").as[Boolean] == true
            ) {
              exec(session =>
                session
                  .set("bsearchCounter", session("bsearchCounter").as[Int] + 1)
              ).exec(
                  http("extra call ${bsearchCounter}")
                    .post("/internal/bsearch")
                    .headers(headers)
                    .header("Referer", baseUrl + "/app/visualize")
                    .body(ElFileBody(queryJson.replaceAll("\\.", "_req.")))
                    .asJson
                    .check(status.is(200).saveAs("status"))
                    .check(jsonPath("$..isPartial").saveAs("isPartial"))
                )
                .exitHereIfFailed
                .pause(1)
                .exec()
            }
        }
      }
  }
}
