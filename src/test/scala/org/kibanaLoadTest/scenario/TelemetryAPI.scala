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
