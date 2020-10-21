package org.kibanaLoadTest.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.kibanaLoadTest.KibanaConfiguration

class BaseSimulation extends Simulation{
  val env = Option(System.getenv("env")).getOrElse("local")

  println(s"Running ${getClass.getSimpleName} simulation with ${env} config")
  val appConfig = new KibanaConfiguration(s"config/${env}.conf")

  val httpProtocol = http
    .baseUrl(appConfig.baseUrl)
    .inferHtmlResources(BlackList(""".*\.js""", """.*\.css""", """.*\.gif""", """.*\.jpeg""", """.*\.jpg""", """.*\.ico""", """.*\.woff""", """.*\.woff2""", """.*\.(t|o)tf""", """.*\.png""", """.*detectportal\.firefox\.com.*"""), WhiteList())
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
    .userAgentHeader("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  var defaultHeaders = Map(
    "Connection" -> "keep-alive",
    "kbn-version" -> appConfig.buildVersion,
    "Content-Type" -> "application/json",
    "Accept" -> "*/*",
    "Origin" -> appConfig.baseUrl,
    "Sec-Fetch-Site" -> "same-origin",
    "Sec-Fetch-Mode" -> "cors",
    "Sec-Fetch-Dest" -> "empty"
  )

  var defaultTextHeaders = Map("Content-Type" -> "text/html; charset=utf-8")

  if (appConfig.isSecurityEnabled) {
    defaultHeaders += ("Cookie" -> "${Cookie}")
    defaultTextHeaders += ("Cookie" -> "${Cookie}")
  }
}
