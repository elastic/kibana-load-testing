package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.kibanaLoadTest.helpers.Helper

import java.util.Calendar

object Dashboard {
  private val timeseriesDefaultPayload: String =
    Helper.loadJsonString("data/timeSeriesPayload.json")
  private val gaugeDefaultPayload: String =
    Helper.loadJsonString("data/gaugePayload.json")
  def updatePayloadTimeRange(payload: String, start: Int, end: Int) =
    payload
      .replaceAll(
        "(?<=\"min\":\")(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z)(?=\")",
        Helper.getDate(Calendar.DAY_OF_MONTH, start)
      )
      .replace(
        "(?<=\"max\":\")(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z)(?=\")",
        Helper.getDate(Calendar.DAY_OF_MONTH, end)
      )
  val timeseriesPayload =
    updatePayloadTimeRange(timeseriesDefaultPayload, -3, -1)
  val gaugePayload = updatePayloadTimeRange(gaugeDefaultPayload, -7, -1)

  val bSearchPayloadSeq = Seq(1,2,3,4,5,6,7,8,9)

  def load(baseUrl: String, headers: Map[String, String]): ChainBuilder = {
    exec(
      http("query dashboard list")
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
          jsonPath("$.saved_objects[0].id").find.saveAs("dashboardId")
        )
        .check(
          jsonPath(
            "$.saved_objects[0].references[?(@.type=='visualization')]"
          ).findAll
            .transform(_.map(_.replaceAll("\"name(.+?),", "")))
            .saveAs("vizVector")
        )
        .check(
          jsonPath(
            "$.saved_objects[0].references[?(@.type=='map' || @.type=='search')]"
          ).findAll
            .transform(
              _.map(_.replaceAll("\"name(.+?),", ""))
            ) //remove name attribute
            .saveAs("searchAndMapVector")
        )
    ).exec(
      http("query indexPatterns list")
        .get("/api/saved_objects/_find")
        .queryParam("fields", "title")
        .queryParam("per_page", "10000")
        .queryParam("type", "index-pattern")
        .headers(headers)
        .header("Referer", baseUrl + "/app/dashboards")
        .check(status.is(200))
        .check(jsonPath("$.saved_objects[?(@.type=='index-pattern')].id").saveAs("indexPatternId"))
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
          //convert Vector -> String for visualizations request
          session.set(
            "vizListString",
            session("vizVector").as[Seq[String]].mkString(",")
          )
        )
        .exec(session =>
          //convert Vector -> String for search&map request
          session.set(
            "searchAndMapString",
            session("searchAndMapVector").as[Seq[String]].mkString(",")
          )
        )
        .exec(
          http("query visualizations")
            .post("/api/saved_objects/_bulk_get")
            .body(StringBody("[${vizListString}, {\"id\":\"${indexPatternId}\", \"type\":\"index-pattern\"}]"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
        .exec(
          http("query search & map")
            .post("/api/saved_objects/_bulk_get")
            .body(StringBody("""[ ${searchAndMapString} ]""".stripMargin))
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
            .body(StringBody(timeseriesPayload))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
        .exec(
          http("query gauge data")
            .post("/api/metrics/vis/data")
            .body(StringBody(gaugePayload))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200))
        )
        .foreach(bSearchPayloadSeq, "index"){
          exec(http("query bsearch ${index}")
            .post("/internal/bsearch")
            .body(ElFileBody("data/bsearch${index}.json"))
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(status.is(200)))

        }
      }
  }
}
