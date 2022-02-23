package org.kibanaLoadTest.scenario

import cats.implicits.catsSyntaxSemigroup

import io.gatling.core.Predef.{exec, _}
import io.gatling.http.Predef._

object TelemetryAPI {
  def load(baseUrl: String, headers: Map[String, String]) = {
    val defaultHeaders =
      headers.combine(Map("Referer" -> s"$baseUrl/app/home"))

    exec(
      http("telemetry: /api/telemetry/v2/clusters/_stats")
        .post("/api/telemetry/v2/clusters/_stats")
        .body(StringBody("""{ "refreshCache": true }""")) // Request for fresh cache so we can measure the effect of generating the telemetry report multiple times
        .headers(defaultHeaders)
        .check(status.is(200))
    )
  }

  def getUnencryptedStats(baseUrl: String, headers: Map[String, String]) = {
    val defaultHeaders =
      headers.combine(Map("Referer" -> s"$baseUrl/app/home"))

    exec(
      http("telemetry: cached /api/telemetry/v2/clusters/_stats")
        .post("/api/telemetry/v2/clusters/_stats")
        .body(StringBody("""{ "unencrypted": true, "refreshCache": true }"""))
        .asJson
        .headers(defaultHeaders)
        .check(status.is(200))
        .check(jsonPath("$").count.is(1))
        .check(
          jsonPath("$[0].stats.stack_stats.kibana.plugins.usage_collector_stats")
            .exists
        )
        .check(
          jsonPath("$[0].stats.stack_stats.kibana.plugins.usage_collector_stats.failed.count")
            .ofType[Int]
            .is(0)
        )
        .check(
          jsonPath("$[0].stats.stack_stats.kibana.plugins.usage_collector_stats.not_ready.count")
            .ofType[Int]
            .is(0)
        )
        .check(
          jsonPath("$[0].stats.stack_stats.kibana.plugins.usage_collector_stats.not_ready_timeout.count")
            .ofType[Int]
            .is(0)
        )
    )
  }

  def cached(baseUrl: String, headers: Map[String, String]) = {
    val defaultHeaders =
      headers.combine(Map("Referer" -> s"$baseUrl/app/home"))

    exec(
      http("telemetry: cached /api/telemetry/v2/clusters/_stats")
        .post("/api/telemetry/v2/clusters/_stats")
        .body(StringBody("""{ "refreshCache": false }"""))
        .headers(defaultHeaders)
        .check(status.is(200))
    )
  }
}
