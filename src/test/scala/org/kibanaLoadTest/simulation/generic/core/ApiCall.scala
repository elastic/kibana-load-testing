package org.kibanaLoadTest.simulation.generic.core

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.simulation.generic.mapping

object ApiCall {
  def execute(
      requests: List[mapping.Request],
      config: KibanaConfiguration
  ): ChainBuilder = {
    // Workaround for https://github.com/gatling/gatling/issues/3783
    val parent :: children = requests
    val httpParentRequest = httpRequest(parent.http, config)
    if (children.isEmpty) {
      exec(httpParentRequest)
    } else {
      val childHttpRequests: Seq[HttpRequestBuilder] =
        children.map(request => httpRequest(request.http, config))
      exec(httpParentRequest.resources(childHttpRequests: _*))
    }
  }

  def httpRequest(
      request: mapping.Http,
      config: KibanaConfiguration
  ): HttpRequestBuilder = {
    val excludeHeaders = List(
      "Content-Length",
      "Kbn-Version",
      "Traceparent",
      "Authorization",
      "Cookie",
      "X-Kbn-Context"
    )
    val defaultHeaders = request.headers.--(excludeHeaders.iterator)

    var headers = defaultHeaders
    if (request.headers.contains("Kbn-Version")) {
      headers += ("Kbn-Version" -> config.buildVersion)
    }
    if (request.headers.contains("Cookie") && config.setCookieHeader) {
      headers += ("Cookie" -> "#{sid}")
    }
    val requestName = s"${request.method} ${request.path
      .replaceAll(".+?(?=\\/bundles)", "") + request.query.getOrElse("")}"
    // Bundle path contains buildNumber which is needs to be changed the value of tested Kibana instance
    // Example: /9007199254740991/bundles/core/core.entry.js
    val url =
      (if (request.path.contains("bundles"))
         request.path.replaceAll("[^\\/]+\\d{10,}", config.buildNumber.toString)
       else request.path) + request.query.getOrElse("")
    request.method match {
      case "GET" =>
        http(requestName)
          .get(url)
          .headers(headers)
          .check(status.is(request.statusCode))
      case "POST" =>
        request.body match {
          case Some(value) =>
            // https://gatling.io/docs/gatling/reference/current/http/request/#stringbody
            // Gatling uses #{value} syntax to pass session values, we disable it by replacing # with ##
            // $ was deprecated, but Gatling still identifies it as session attribute
            val bodyString = value.replaceAll("\\$\\{\\{", "{{")
            http(requestName)
              .post(url)
              .body(StringBody(bodyString))
              .asJson
              .headers(headers)
              .check(status.is(request.statusCode))
          case _ =>
            http(requestName)
              .post(url)
              .asJson
              .headers(headers)
              .check(status.is(request.statusCode))
        }
      case "PUT" =>
        http(requestName)
          .put(url)
          .headers(headers)
          .check(status.is(request.statusCode))
      case "DELETE" =>
        http(requestName)
          .delete(url)
          .headers(headers)
          .check(status.is(request.statusCode))
      case _ =>
        throw new IllegalArgumentException(s"Invalid method ${request.method}")
    }
  }
}
