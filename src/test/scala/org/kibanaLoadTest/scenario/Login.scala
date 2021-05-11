package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

object Login {
  private val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  def doLogin(
      isSecurityEnabled: Boolean,
      loginPayload: String,
      loginStatusCode: Int
  ): ChainBuilder =
    doIf(isSecurityEnabled) {
      exec(
        http("login")
          .post("/internal/security/login")
          .headers(loginHeaders)
          .body(StringBody(loginPayload))
          .asJson
          .check(headerRegex("set-cookie", ".+?(?=;)").saveAs("Cookie"))
          .check(status.is(loginStatusCode))
      )
    }.exitHereIfFailed

  def openKibana(): ChainBuilder =
    exec(
      http("start: spaces/_active_space")
        .get("/internal/spaces/_active_space")
        .check(status.is(401))
    ).exec(
        http("start: security/me")
          .get("/internal/security/me")
          .header("kbn-system-request", "true")
          .check(status.is(401))
      )
      .exec(
        http("start: core/capabilities")
          .post("/api/core/capabilities")
          .queryParam("useDefaultCapabilities", "true")
          .body(ElFileBody("payload/core/capabilities.json"))
          .asJson
          .check(status.is(200))
      )
      .exec(
        http("start: banners/info")
          .get("/api/banners/info")
          .check(status.is(401))
      )
      .exec(
        http("start: licensing/info")
          .get("/api/licensing/info")
          .check(status.is(401))
      )
      .exec(
        http("start: fleet/epm/packages")
          .get("/api/fleet/epm/packages?experimental=true")
          .check(status.is(401))
      )
      .exec(
        http("start: security/login_state")
          .get("/internal/security/login_state")
          .check(status.is(200))
      )
}
