package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt

object Dashboard {
  def load(baseUrl: String, headers: Map[String, String]): ChainBuilder =
    exec(
      http("query indexPattern")
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
        http("query dashboard list")
          .get("/api/saved_objects/_find")
          .queryParam("default_search_operator", "AND")
          .queryParam("page", "1")
          .queryParam("per_page", "1000")
          .queryParam("search_fields", "title%5E3")
          .queryParam("search_fields", "description")
          .queryParam("type", "dashboard")
          .headers(headers)
          .header("Referer", baseUrl + "/app/dashboards")
          .check(jsonPath("$.saved_objects[:1].id").saveAs("dashboardId"))
          .check(status.is(200))
      ).pause(2 seconds)
        .exec(
          http("query panels list")
            .post("/api/saved_objects/_bulk_get")
            .body(
              StringBody(
                """
          [
            {
              "id":"${dashboardId}",
              "type":"dashboard"
            }
          ]
        """
              )
            )
            .asJson
            .headers(headers)
            .header("Referer", baseUrl + "/app/dashboards")
            .check(
              jsonPath(
                "$.saved_objects[0].references[?(@.type=='visualization')]"
              ).findAll
                .transform(
                  _.map(_.replaceAll("\"name(.+?),", ""))
                ) //remove name attribute
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
            .check(status.is(200))
        )
        .exec(session =>
          //convert Vector -> String
          session.set(
            "vizListString",
            session("vizVector").as[Seq[String]].mkString(",")
          )
        )
        .exec(session => {
          //convert Vector -> String
          session.set(
            "searchAndMapString",
            session("searchAndMapVector").as[Seq[String]].mkString(",")
          )
        })
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
          http("query search & map")
            .post("/api/saved_objects/_bulk_get")
            .body(StringBody("""[ ${searchAndMapString} ]""".stripMargin))
            .asJson
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
