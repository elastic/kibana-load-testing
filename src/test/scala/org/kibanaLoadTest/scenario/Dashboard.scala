package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper
import java.util.Calendar

object Dashboard {
  // bsearch1.json ... bsearch4.json
  private val bSearchPayloadSeq = Seq(1, 2, 3, 4)

  def load(baseUrl: String, headers: Map[String, String]): ChainBuilder = {
    exec(// unique search sessionId for each virtual user
      session => session.set("sessionId", Helper.generateUUID)
    ).exec(// unique date picker start time for each virtual user
      session => session.set("startTime", Helper.getDate(Calendar.DAY_OF_MONTH, -7))
    ).exec(// unique date picker end time for each virtual user
      session => session.set("endTime", Helper.getDate(Calendar.DAY_OF_MONTH, 0))
    ).exec(// unique date picker end time for each virtual user
      session => session.set("7dBeforeTime", Helper.getDate(Calendar.DAY_OF_MONTH, -14))
    ).exec(
      http("query dashboards list")
        .get("/api/saved_objects/_find")
        .queryParam("default_search_operator", "AND")
        .queryParam("has_reference", "[]")
        .queryParam("page", "1")
        .queryParam("per_page", "1000")
        .queryParam("search_fields", "title^3")
        .queryParam("search_fields", "description")
        .queryParam("type", "dashboard")
        .headers(headers)
        .header("Referer", baseUrl + "/app/dashboards")
        .check(status.is(200))
        .check(
          jsonPath("$.saved_objects[?(@.attributes.title=='[eCommerce] Revenue Dashboard')].id").find.saveAs("dashboardId")
        )
        .check(
          jsonPath(
            "$.saved_objects[?(@.attributes.title=='[eCommerce] Revenue Dashboard')].references[?(@.type=='visualization')]"
          ).findAll
            .transform(_.map(_.replaceAll("\"name(.+?),", "")))
            .saveAs("vizVector")
        )
        .check(
          jsonPath(
            "$.saved_objects[?(@.attributes.title=='[eCommerce] Revenue Dashboard')].references[?(@.type=='map')]"
          ).findAll.transform(_.map(_.replaceAll("\"name(.+?),", ""))).saveAs("mapVector")
        )
        .check(
          jsonPath(
            "$.saved_objects[?(@.attributes.title=='[eCommerce] Revenue Dashboard')].references[?(@.type=='search')]"
          ).findAll.transform(_.map(_.replaceAll("\"name(.+?),", ""))).saveAs("searchVector")
        )
        .check(
          jsonPath(
            "$.saved_objects[?(@.attributes.title=='[eCommerce] Revenue Dashboard')].references[?(@.type=='lens')]"
          ).findAll.transform(_.map(_.replaceAll("\"name(.+?),", ""))).saveAs("lensVector")
        )
    ).pause(1).exec(
      http("query index pattern")
        .get("/api/saved_objects/_find")
        .queryParam("fields", "title")
        .queryParam("fields", "type")
        .queryParam("fields", "typeMeta")
        .queryParam("per_page", "10000")
        .queryParam("type", "index-pattern")
        .headers(headers)
        .header("Referer", baseUrl + "/app/dashboards")
        .check(status.is(200))
        .check(
          jsonPath("$.saved_objects[?(@.attributes.title=='kibana_sample_data_ecommerce')].id")
            .saveAs("indexPatternId")
        )
    )
      .exitBlockOnFail {
        exec(
          http("query dashboard panels")
            .post("/api/saved_objects/_bulk_get")
            .body(
              StringBody("[{\"id\":\"#{dashboardId}\",\"type\":\"dashboard\"}]")
            )
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).exec(session =>
          //convert Vector -> String for visualizations request
          session.set(
            "vizListString",
            session("vizVector").as[Seq[String]].mkString(",")
          )
        ).exec(session =>
          //convert Vector -> String for search request
          session.set(
            "searchString",
            session("searchVector").as[Seq[String]].mkString(",")
          )
        ).exec(session =>
          //convert Vector -> String for map request
          session.set(
            "mapString",
            session("mapVector").as[Seq[String]].mkString(",")
          )
        ).exec(session =>
          //convert Vector -> String for search&map request
          session.set(
            "lensString",
            session("lensVector").as[Seq[String]].mkString(",")
          )).exec(
          http("query visualizations")
            .post("/api/saved_objects/_bulk_resolve")
            .body(StringBody("[#{vizListString}]"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).exec(
          http("bulk_get: search")
            .post("/api/saved_objects/_bulk_get")
            .body(StringBody("[#{searchString}]"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).exec(
          http("bulk_resolve: map")
            .post("/api/saved_objects/_bulk_resolve")
            .body(StringBody("[#{mapString}]"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).exec(
          http("bulk_resolve: lens")
          .post("/api/saved_objects/_bulk_resolve")
          .body(StringBody("[#{lensString}]"))
          .asJson
          .headers(headers)
          .header("Referer", baseUrl + "/app/dashboards")
          .check(status.is(200))
        ).exec(
          http("bulk_resolve: index pattern")
            .post("/api/saved_objects/_bulk_resolve")
            .body(
              StringBody(
                "[{\"id\":\"#{indexPatternId}\",\"type\":\"index-pattern\"}]"
              )
            )
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
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
              .header("Referer", baseUrl + "/app/dashboards")
              .check(status.is(200))
          ).exec(
          http("query index pattern search fields")
            .get("/api/saved_objects/_find")
            .queryParam("fields", "title")
            .queryParam("per_page", "10")
            .queryParam("search", "kibana_sample_data_ecommerce")
            .queryParam("search_fields", "title")
            .queryParam("type", "index-pattern")
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).pause(1).exec(
          http("query timeseries data")
            .post("/api/metrics/vis/data")
            .body(ElFileBody("data/dashboard/timeSeriesPayload.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).pause(1).exec(
          http("query gauge data")
            .post("/api/metrics/vis/data")
            .body(ElFileBody("data/dashboard/gaugePayload.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        ).foreach(bSearchPayloadSeq, "index") {
          exec(session => {
            session.set(
              "payloadString",
              Helper.loadJsonString(s"data/dashboard/bsearch${session("index").as[Int]}.json")
            )
          }).pause(2).exec(
            http("query bsearch #{index}")
              .post("/internal/bsearch")
              .queryParam("compress", "true")
              .body(StringBody("#{payloadString}"))
              .asJson
              .headers(headers)
              .header("Referer", baseUrl + "/app/dashboards")
              .check(status.is(200))
          )
        }
      }
  }
}
