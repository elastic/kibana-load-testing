package org.kibanaLoadTest.scenario

import cats.implicits.catsSyntaxSemigroup

import io.gatling.core.Predef.{exec, _}
import io.gatling.http.Predef._

object Home {
  def load(baseUrl: String, headers: Map[String, String]) = {
    val defaultHeaders =
      headers.combine(Map("Referer" -> s"$baseUrl/app/home"))

    exec(
      http("home: security/me")
        .get("/internal/security/me")
        .headers(defaultHeaders)
        .check(status.is(200))
    ).pause(1)
      .exec(
        http("home: security/session")
          .get("/internal/security/session")
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .pause(1)
      .exec(
        http("home: has_user_index_pattern")
          .get("/api/index_patterns/has_user_index_pattern")
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .pause(1)
      .exec(
        http("home: list_alert_types")
          .get("/api/alerts/list_alert_types")
          .headers(defaultHeaders)
          .check(status.is(200))
      )
      .pause(1)
      .exec(
        http("home: active_space")
          .get("/internal/spaces/_active_space")
          .headers(defaultHeaders)
          .check(status.is(200))
      )
  }
}
