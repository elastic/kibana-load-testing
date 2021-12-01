package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.structure.ChainBuilder
import org.kibanaLoadTest.helpers.Helper

import java.util.Calendar

object Lens {
  def load(
      id: String,
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
        http("bulk_resolve: lens")
          .post("/api/saved_objects/_bulk_resolve")
          .headers(headers)
          .header("Referer", baseUrl + "/app/lens")
          .body(
            StringBody("[{\"id\":\"${vizId}\",\"type\":\"lens\"}]")
          )
          .check(status.is(200))
          .check(
            jsonPath(
              "$..saved_object.references[?(@.name=='indexpattern-datasource-current-indexpattern')].id"
            ).find
              .saveAs("indexPatternId")
          )
      )
      .pause(1)
      .exec(
        http("bulk_resolve: index-pattern")
          .post("/api/saved_objects/_bulk_resolve")
          .headers(headers)
          .header("Referer", baseUrl + "/app/lens")
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
          .header("Referer", baseUrl + "/app/lens")
          .check(status.is(200))
      )
      .exec(
        http("lens/existing_fields")
          .post("/api/lens/existing_fields/${indexPatternId}")
          .headers(headers)
          .header("Referer", baseUrl + "/app/lens")
          .body(ElFileBody(s"data/lens/$id/existing_fields.json"))
          .check(status.is(200))
      )
      .exec(
        http("bsearch")
          .post("/internal/bsearch")
          .queryParam("compress", "true")
          .headers(headers)
          .header("Referer", baseUrl + "/app/lens")
          .body(ElFileBody(s"data/lens/$id/bsearch.json"))
          .asJson
          .check(status.is(200))
      )
  }
}
