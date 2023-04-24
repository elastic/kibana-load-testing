package org.kibanaLoadTest.helpers

import io.gatling.core.Predef._
import io.gatling.core.filter.DenyList
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.slf4j.{Logger, LoggerFactory}

class HttpHelper(config: KibanaConfiguration) {
  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-version" -> config.buildVersion
  )
  val logger: Logger = LoggerFactory.getLogger("HttpHelper")

  def getDefaultHeaders: Map[String, String] = {
    Map(
      "Connection" -> "keep-alive",
      "kbn-version" -> config.buildVersion,
      "Content-Type" -> "application/json",
      "Accept" -> "*/*",
      "Origin" -> config.baseUrl,
      "Sec-Fetch-Site" -> "same-origin",
      "Sec-Fetch-Mode" -> "cors",
      "Sec-Fetch-Dest" -> "empty"
    )
  }

  def defaultTextHeaders: Map[String, String] = {
    Map("Content-Type" -> "text/html; charset=utf-8")
  }

  def getProtocol: HttpProtocolBuilder = {
    http
      .baseUrl(config.baseUrl)
      .inferHtmlResources(
        allow = AllowList(),
        deny = new DenyList(
          Seq(
            """.*\.js""",
            """.*\.svg""",
            """.*\.css""",
            """.*\.gif""",
            """.*\.jpeg""",
            """.*\.jpg""",
            """.*\.ico""",
            """.*\.woff""",
            """.*\.woff2""",
            """.*\.(t|o)tf""",
            """.*\.png""",
            """.*detectportal\.firefox\.com.*"""
          )
        )
      )
      .acceptHeader(
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
      )
      .acceptEncodingHeader("gzip, deflate, br")
      .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
      .userAgentHeader(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36"
      )
  }
}
