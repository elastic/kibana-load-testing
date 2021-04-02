package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

object Dashboard {
  def load(baseUrl: String, headers: Map[String, String]): ChainBuilder = {
    exec(
      http("query dashboard list")
        .get("/api/saved_objects/_find")
        .queryParam("default_search_operator", "AND")
        .queryParam("has_reference", "%5B%5D")
        .queryParam("page", "1")
        .queryParam("per_page", "1000")
        .queryParam("search_fields", "title%5E3")
        .queryParam("search_fields", "description")
        .queryParam("type", "dashboard")
        .headers(headers)
        .header("Referer", baseUrl + "/app/dashboards")
        .check(status.is(200))
        .check(
          jsonPath("$.saved_objects[?(@.type=='dashboard')].id")
            .saveAs("dashboardId")
        )
        .check(jsonPath("$.saved_objects.[?(@.type=='visualization')]")
          .findAll
          .transform(_.map(_.replaceAll("\"name(.+?),", "")))
          .saveAs("vizVector")
        )
        .check(jsonPath("$.saved_objects.[?(@.type=='map')]")
          .findAll
          .transform(_.map(_.replaceAll("\"name(.+?),", "")))
          .saveAs("mapVector")
        )
        .check(
          jsonPath("$.saved_objects.[?(@.type=='search')]")
            .findAll
            .transform(_.map(_.replaceAll("\"name(.+?),", "")))
            .saveAs("searchVector")
        )
    )
    .exec(
      http("query indexPatterns list")
        .get("/api/saved_objects/_find")
        .queryParam("fields", "title")
        .queryParam("per_page", "10000")
        .queryParam("type", "index-pattern")
        .headers(headers)
        .header("Referer", baseUrl + "/app/dashboards")
        .check(status.is(200))
        .check(
          jsonPath("$.saved_objects[?(@.type=='index-pattern')].id")
            .saveAs("indexPatternId")
        )
    ).exitBlockOnFail {
        exec(
          http("query panels list")
            .post("/api/saved_objects/_bulk_get")
            .body(StringBody("[{\"id\":\"${dashboardId}\",\"type\":\"dashboard\"}]"))
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
        .exec(session =>
          //convert Vector -> String
          session.set(
            "vizListString",
            session("vizVector").as[Seq[String]].mkString(",")
          )
        )
        .exec(session =>
          //convert Vector -> String
          session.set(
            "mapListString",
            session("mapVector").as[Seq[String]].mkString(",")
          )
        )
          .exec(session =>
            //convert Vector -> String
            session.set(
              "searchListString",
              session("searchVector").as[Seq[String]].mkString(",")
            )
          )
        .exec(
          http("query visualizations")
            .post("/api/saved_objects/_bulk_get")
            .body(
              StringBody(
                "[" +
                  "${vizListString}"
                    .concat(
                      ", { \"id\":\"${indexPatternId}\", \"type\":\"index-pattern\"  }]"
                    )
              )
            )
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
          .exec(
            http("query map")
              .post("/api/saved_objects/_bulk_get")
              .body(
                StringBody(
                  "[" +
                    "$mapListString}"
                      .concat(
                        ", { \"id\":\"${indexPatternId}\", \"type\":\"index-pattern\"  }]"
                      )
                )
              )
              .asJson
              .headers(headers)
              .header("Referer", baseUrl + "/app/dashboards")
              .check(status.is(200))
          )
          .exec(
            http("query search")
              .post("/api/saved_objects/_bulk_get")
              .body(
                StringBody(
                  "[" +
                    "$searchListString}"
                      .concat(
                        ", { \"id\":\"${indexPatternId}\", \"type\":\"index-pattern\"  }]"
                      )
                )
              )
              .asJson
              .headers(headers)
              .header("Referer", baseUrl + "/app/dashboards")
              .check(status.is(200))
          )
          .exec(http("query eCommerce fields for wildcard")
            .get("/api/index_patterns/_fields_for_wildcard")
            .queryParam("pattern", "kibana_sample_data_ecommerce")
            .queryParam("meta_fields", "_source")
            .queryParam("meta_fields", "_id")
            .queryParam("meta_fields", "_type")
            .queryParam("meta_fields", "_index")
            .queryParam("meta_fields", "_score")
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
          )
          .exec(http("query eCommerce index pattern")
            .get("/api/saved_objects/_find")
            .queryParam("fields", "title")
            .queryParam("per_page", "10")
            .queryParam("search", "kibana_sample_data_ecommerce")
            .queryParam("search_fields", "title")
            .queryParam("type", "index-pattern")
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
          )
          .exec(http("query input control settings")
            .get("/api/input_control_vis/settings")
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
          )
          .exec(
          http("query timeseries data")
            .post("/api/metrics/vis/data")
            .body(ElFileBody("data/timeSeriesPayload.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
        .exec(
          http("query gauge data")
            .post("/api/metrics/vis/data")
            .body(ElFileBody("data/gaugePayload.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
    }
  }
}
