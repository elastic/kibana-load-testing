package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

object Home {
  def loadHome(baseUrl: String): ChainBuilder =
    exec(
      http("home: spaces/_active_space")
        .get("/internal/spaces/_active_space")
        .header("Referer", baseUrl + "/app/home")
        .check(status.is(200))
    ).exec(
        http("home: security/me")
          .get("/internal/security/me")
          .header("Referer", baseUrl + "/app/home")
          .header("kbn-system-request", "true")
          .check(status.is(200))
      )
      .exec(
        http("home: core/capabilities")
          .post("/api/core/capabilities")
          .header("Referer", baseUrl + "/app/home")
          .body(ElFileBody("payload/core/capabilities.json"))
          .asJson
          .check(status.is(200))
      )
      .exec(
        http("home: banners/info")
          .get("/api/banners/info")
          .check(status.is(200))
      )
      .exec(
        http("home: licensing/info")
          .get("/api/licensing/info")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: licensing/feature_usage/register")
          .post("/internal/licensing/feature_usage/register")
          .header("Referer", baseUrl + "/app/home")
          .body(ElFileBody("payload/licensing/feature_usage_register.json"))
          .asJson
          .check(status.is(200))
      )
      .exec(
        http("home: security_oss/app_state")
          .get("/internal/security_oss/app_state")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: security/me")
          .get("/internal/security/me")
          .header("Referer", baseUrl + "/app/home")
          .header("kbn-system-request", "true")
          .check(status.is(200))
      )
      .exec(
        http("home: saved_objects_tagging/tags")
          .get("/api/saved_objects_tagging/tags")
          .header("Referer", baseUrl + "/app/home")
          .header("kbn-system-request", "true")
          .check(status.is(200))
      )
      .exec(
        http("home: licensing/info")
          .get("/api/licensing/info")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: fleet/epm/packages")
          .get("/api/fleet/epm/packages?experimental=true")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: telemetry")
          .post("/api/saved_objects/_bulk_get")
          .header("Referer", baseUrl + "/app/home")
          .body(ElFileBody("payload/saved_objects/telemetry.json"))
          .asJson
          .check(status.is(200))
      )
      .exec(
        http("home: security/me")
          .get("/internal/security/me")
          .header("Referer", baseUrl + "/app/home")
          .header("kbn-system-request", "true")
          .check(status.is(200))
      )
      .exec(
        http("home: global_search/searchable_types")
          .get("/internal/global_search/searchable_types")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("security/session")
          .get("/internal/security/session")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: alerts/list_alert_types")
          .get("/api/alerts/list_alert_types")
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: saved_objects/_find")
          .get(
            "/api/saved_objects/_find?fields=title&per_page=1&search=*&search_fields=title&type=index-pattern"
          )
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
      .exec(
        http("home: global_search/find")
          .post("/internal/global_search/find")
          .body(ElFileBody("payload/global_search/find.json"))
          .asJson
          .header("Referer", baseUrl + "/app/home")
          .check(status.is(200))
      )
}
