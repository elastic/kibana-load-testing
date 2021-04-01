package org.kibanaLoadTest.helpers
import spray.json.DefaultJsonProtocol._
import spray.json._

object GatlingStats {
  case class Request(total: Double, ok: Double, ko: Double)
  case class Group(name: String, count: Long, percentage: Int)
  case class Stats(
      numberOfRequests: Request,
      minResponseTime: Request,
      maxResponseTime: Request,
      meanResponseTime: Request,
      standardDeviation: Request,
      percentiles1: Request,
      percentiles2: Request,
      percentiles3: Request,
      percentiles4: Request,
      group1: Group,
      group2: Group,
      group3: Group,
      group4: Group,
      meanNumberOfRequestsPerSecond: Request
  )

  def parse(gatlingStatsJson: String): Stats = {
    implicit val metaFormat: JsonFormat[Request] = jsonFormat3(Request)
    implicit val userFormat: JsonFormat[Group] = jsonFormat3(Group)
    implicit val responseFormat: JsonFormat[Stats] = jsonFormat14(Stats)
    gatlingStatsJson.parseJson.convertTo[Stats]
  }

  def toJsonString(jsonString: String): String = {
    val stats = parse(jsonString)
    s"""
       |{
       | "numberOfRequests_total": ${stats.numberOfRequests.total.round},
       | "numberOfRequests_ok": ${stats.numberOfRequests.ok.round},
       | "numberOfRequests_ko": ${stats.numberOfRequests.ko.round},
       | "minResponseTime_total": ${stats.minResponseTime.total.round},
       | "minResponseTime_ok": ${stats.minResponseTime.ok.round},
       | "minResponseTime_ko": ${stats.minResponseTime.ko.round},
       | "maxResponseTime_total": ${stats.maxResponseTime.total.round},
       | "maxResponseTime_ok": ${stats.maxResponseTime.ok.round},
       | "maxResponseTime_ko": ${stats.maxResponseTime.ko.round},
       | "meanResponseTime_total": ${stats.meanResponseTime.total.round},
       | "meanResponseTime_ok": ${stats.meanResponseTime.ok.round},
       | "meanResponseTime_ko": ${stats.meanResponseTime.ko.round},
       | "standardDeviation_total": ${stats.standardDeviation.total.round},
       | "standardDeviation_ok": ${stats.standardDeviation.ok.round},
       | "standardDeviation_ko": ${stats.standardDeviation.ko.round},
       | "50_pct_total": ${stats.percentiles1.total.round},
       | "50_pct_ok": ${stats.percentiles1.ok.round},
       | "50_pct_ko": ${stats.percentiles1.ko.round},
       | "75_pct_total": ${stats.percentiles2.total.round},
       | "75_pct_ok": ${stats.percentiles2.ok.round},
       | "75_pct_ko": ${stats.percentiles2.ko.round},
       | "95_pct_total": ${stats.percentiles3.total.round},
       | "95_pct_ok": ${stats.percentiles3.ok.round},
       | "95_pct_ko": ${stats.percentiles3.ko.round},
       | "99_pct_total": ${stats.percentiles4.total.round},
       | "99_pct_ok": ${stats.percentiles4.ok.round},
       | "99_pct_ko": ${stats.percentiles4.ko.round},
       | "group1_name": "${stats.group1.name}",
       | "group1_count": ${stats.group1.count},
       | "group1_percentage": ${stats.group1.percentage},
       | "group2_name": "${stats.group2.name}",
       | "group2_count": ${stats.group2.count},
       | "group2_percentage": ${stats.group2.percentage},
       | "group3_name": "${stats.group3.name}",
       | "group3_count": ${stats.group3.count},
       | "group3_percentage": ${stats.group3.percentage},
       | "group4_name": "${stats.group4.name}",
       | "group4_count": ${stats.group4.count},
       | "group4_percentage": ${stats.group4.percentage},
       | "meanNumberOfRequestsPerSecond_total": ${stats.meanNumberOfRequestsPerSecond.total},
       | "meanNumberOfRequestsPerSecond_ok": ${stats.meanNumberOfRequestsPerSecond.ok},
       | "meanNumberOfRequestsPerSecond_ko": ${stats.meanNumberOfRequestsPerSecond.ko}
       |}
    """.stripMargin
  }
}
