package org.kibanaLoadTest.test

import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpTemplate.TemplateType
import org.mockserver.model.HttpTemplate.template
import org.mockserver.model.JsonBody.json

object ServerHelper {
  def mockKibanaStatus(mockServer: ClientAndServer) = {
    mockServer
      .when(request.withPath("/api/status").withMethod("GET"))
      .respond(
        response()
          .withStatusCode(200)
          .withBody(
            json(
              """{"version":{"build_hash":"abcdefg","build_number":9007199,"build_snapshot":true,"number":"8.8.0"}}"""
            )
          )
      )
  }

  def mockEsStatus(mockServer: ClientAndServer) = {
    mockServer
      .when(request.withPath("/").withMethod("GET"))
      .respond(
        response()
          .withStatusCode(200)
          .withBody(
            json(
              """{"version":{"number":"8.8.0-SNAPSHOT","build_hash":"2be475ce39c1c09d","build_date":"2023-04-04T14:09:23.609840352Z","lucene_version" : "9.6.0"}}"""
            )
          )
      )
  }

  def mockKibanaLogin(mockServer: ClientAndServer) = {
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withPath("/internal/security/login")
          .withHeader("Content-Type", "application/json")
          .withHeader("kbn-xsrf", "xsrf")
      )
      .respond(
        template(
          TemplateType.MUSTACHE,
          "{\n" +
            "     'statusCode': 200,\n" +
            "     'headers': {\n" +
            "          'set-cookie': [ 'sid=Fe26.2**{{ uuid }}; HttpOnly; Path=/' ]\n" +
            "     },\n" +
            "}"
        )
      )
  }

  def mockKibanaSampleData(
      mockServer: ClientAndServer,
      dataType: String,
      buildVersion: String
  ) = {
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withPath(s"/api/sample_data/$dataType")
          .withHeader("kbn-version", buildVersion)
      )
      .respond(
        response()
          .withStatusCode(200)
      )

    mockServer
      .when(
        request()
          .withMethod("DELETE")
          .withPath(s"/api/sample_data/$dataType")
          .withHeader("kbn-version", buildVersion)
      )
      .respond(
        response()
          .withStatusCode(204)
      )
  }
}
