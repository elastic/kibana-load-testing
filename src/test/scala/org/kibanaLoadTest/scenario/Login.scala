package org.kibanaLoadTest.scenario

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._

object Login {
  private val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  def doLogin(loginPayload: String): ChainBuilder =
    exec(
      http("login")
        .post("/internal/security/login")
        .headers(loginHeaders)
        .body(StringBody(loginPayload))
        .asJson
        .check(headerRegex("set-cookie", ".+?(?=;)").saveAs("Cookie"))
        .check(status.is(200))
    ).exitHereIfFailed
}
