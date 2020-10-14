package com.kibanaTest

import java.io.File
import java.util.Calendar

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.getLastReportPath
import org.kibanaLoadTest.helpers.{ESWrapper, Helper, HttpHelper}

import scala.concurrent.duration.DurationInt

class DemoJourney extends Simulation {

  val env = Option(System.getenv("env")).getOrElse("local")

  println(s"Running Kibana ${env} config")
  val appConfig = new KibanaConfiguration(s"config/${env}.conf")
  println(s"Base URL = ${appConfig.baseUrl}")
  println(s"Kibana version = ${appConfig.buildVersion}")
  println(s"Security Enabled = ${appConfig.isSecurityEnabled}")
  println(s"Auth payload = ${appConfig.loginPayload}")

  val discoverPayload = Helper.loadJsonString("data/discoverPayload.json")
  val discoverPayloadQ1 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -1))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 0))

  val discoverPayloadQ2 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -14))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -5))

  val discoverPayloadQ3 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -20))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 20))

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

  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  if (appConfig.isSecurityEnabled) {
    defaultHeaders += ("Cookie" -> "${Cookie}")
    defaultTextHeaders += ("Cookie" -> "${Cookie}")
  }

  val scenarioName = s"Kibana demo journey ${appConfig.buildVersion} ${env}"

  val scn = scenario(scenarioName)
    .doIf(appConfig.isSecurityEnabled) {
      exec(http("login")
        .post("/internal/security/login")
        .headers(loginHeaders)
        .body(StringBody(appConfig.loginPayload)).asJson
        .check(headerRegex("set-cookie", ".+?(?=;)").saveAs("Cookie"))
        .check(status.is(appConfig.loginStatusCode)))
    }
    .exitHereIfFailed
    .pause(5 seconds)
    .exec(http("Discover query 1")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ1)).asJson
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("Discover query 2")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ2)).asJson
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("Discover query 3")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ3)).asJson
      .check(status.is(200)))
    .pause(10 seconds)
    .exec(http("query indexPattern")
      .get("/api/saved_objects/_find")
      .queryParam("fields", "title")
      .queryParam("per_page", "10000")
      .queryParam("type", "index-pattern")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/dashboards")
      .check(status.is(200))
      .check(jsonPath("$.saved_objects[?(@.type=='index-pattern')].id").saveAs("indexPatternId")))
    .exitBlockOnFail {
      exec(http("query dashboard list")
        .get("/api/saved_objects/_find")
        .queryParam("default_search_operator", "AND")
        .queryParam("page", "1")
        .queryParam("per_page", "1000")
        .queryParam("search_fields", "title%5E3")
        .queryParam("search_fields", "description")
        .queryParam("type", "dashboard")
        .headers(defaultHeaders)
        .header("Referer", appConfig.baseUrl + "/app/dashboards")
        .check(jsonPath("$.saved_objects[:1].id").saveAs("dashboardId"))
        .check(status.is(200)))
        .pause(2 seconds)
        .exec(http("query panels list")
          .post("/api/saved_objects/_bulk_get")
          .body(StringBody(
            """
          [
            {
              "id":"${dashboardId}",
              "type":"dashboard"
            }
          ]
        """
          )).asJson
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/dashboards")
          .check(
            jsonPath("$.saved_objects[0].references[?(@.type=='visualization')]")
              .findAll
              .transform(_.map(_.replaceAll("\"name(.+?),", ""))) //remove name attribute
              .saveAs("vizVector"))
          .check(
            jsonPath("$.saved_objects[0].references[?(@.type=='map' || @.type=='search')]")
              .findAll
              .transform(_.map(_.replaceAll("\"name(.+?),", ""))) //remove name attribute
              .saveAs("searchAndMapVector"))
          .check(status.is(200)))
        .exec(session =>
          //convert Vector -> String
          session.set("vizListString", session("vizVector").as[Seq[String]].mkString(",")))
        .exec(session => {
          //convert Vector -> String
          session.set("searchAndMapString", session("searchAndMapVector").as[Seq[String]].mkString(","))
        })
        .exec(http("query visualizations")
          .post("/api/saved_objects/_bulk_get")
          .body(StringBody("[" +
            "${vizListString}"
              .concat(", { \"id\":\"${indexPatternId}\", \"type\":\"index-pattern\"  }]"))).asJson
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/dashboards")
          .check(status.is(200)))
        .exec(http("query search & map")
          .post("/api/saved_objects/_bulk_get")
          .body(StringBody(
            """[ ${searchAndMapString} ]""".stripMargin)).asJson
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/dashboards")
          .check(status.is(200)))
        .exec(http("query timeseries data")
          .post("/api/metrics/vis/data")
          .body(ElFileBody("data/timeSeriesPayload.json")).asJson
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/dashboards")
          .check(status.is(200)))
        .exec(http("query gauge data")
          .post("/api/metrics/vis/data")
          .body(ElFileBody("data/gaugePayload.json")).asJson
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/dashboards")
          .check(status.is(200)))
    }
      .pause(10 seconds)
      .exec(http("canvas workpads")
        .get("/api/canvas/workpad/find")
        .queryParam("name", "")
        .queryParam("perPage", "10000")
        .headers(defaultHeaders)
        .header("Referer", appConfig.baseUrl + "/app/canvas")
        .check(status.is(200))
        .check()
        .check(jsonPath("$.workpads[0].id").saveAs("workpadId")))
      .exitBlockOnFail {
        exec(http("interpreter demo")
          .get("/api/interpreter/fns")
          .queryParam("name", "")
          .queryParam("perPage", "10000")
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/canvas")
          .check(status.is(200)))
        .pause(5 seconds)
        .exec(http("load workpad")
          .get("/api/canvas/workpad/${workpadId}")
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/canvas")
          .header("kbn-xsrf", "professionally-crafted-string-of-text")
          .check(status.is(200)))
          .pause(1 seconds)
          .exec(http("query canvas timelion")
            .post("/api/timelion/run")
            .body(ElFileBody("data/canvasTimelionPayload.json")).asJson
            .headers(defaultHeaders)
            .header("Referer", appConfig.baseUrl + "/app/canvas")
            .header("kbn-xsrf", "professionally-crafted-string-of-text")
            .check(status.is(200)))
          .pause(1 seconds)
          .exec(http("query canvas aggs 1")
            .post("/api/interpreter/fns")
            .body(ElFileBody("data/canvasInterpreterPayload1.json")).asJson
            .headers(defaultHeaders)
            .header("Referer", appConfig.baseUrl + "/app/canvas")
            .check(status.is(200)))
          .pause(1 seconds)
          .exec(http("query canvas aggs 2")
            .post("/api/interpreter/fns")
            .body(ElFileBody("data/canvasInterpreterPayload2.json")).asJson
            .headers(defaultHeaders)
            .header("Referer", appConfig.baseUrl + "/app/canvas")
            .check(status.is(200)))
      }

  before {
    // load sample data
    new HttpHelper(appConfig)
      .loginIfNeeded()
      .addSampleData("ecommerce")
      .closeConnection()
  }

  after {
    // remove sample data
    try {
      new HttpHelper(appConfig)
        .loginIfNeeded()
        .removeSampleData("ecommerce")
        .closeConnection()
    } catch {
      case e: java.lang.RuntimeException => println(s"Can't remove sample data\n ${e.printStackTrace()}")
    }

    // ingest results to ES instance
    val ingest:Boolean = Option(System.getenv("ingest")).getOrElse("false").toBoolean
    if (ingest) {
      val logFilePath = getLastReportPath() + File.separator + "simulation.log"
      val esWrapper = new ESWrapper(appConfig)
      esWrapper.ingest(logFilePath, scenarioName)
    }
  }

  // setup 1
  setUp(
    scn.inject(
      constantConcurrentUsers(20) during (3 minute), // 1
      rampConcurrentUsers(20) to (50) during (3 minute) // 2
    ).protocols(httpProtocol)
  ).maxDuration(15 minutes)

  // generate a closed workload injection profile
  // with levels of 10, 15, 20, 25 and 30 concurrent users
  // each level lasting 10 seconds
  // separated by linear ramps lasting 10 seconds
}