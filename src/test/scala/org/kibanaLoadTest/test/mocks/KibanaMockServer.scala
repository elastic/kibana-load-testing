package org.kibanaLoadTest.test.mocks

import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpTemplate.{TemplateType, template}
import org.mockserver.model.JsonBody.json
import org.mockserver.stop.Stop.stopQuietly

class KibanaMockServer(port: Int) {
  private val server = ClientAndServer.startClientAndServer(this.port)

  def createKibanaStatusCallback() = {
    server
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

  def createSuccessfulLoginCallback() = {
    server
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

  def createAddSampleDataCallback(
      dataType: String,
      buildVersion: String
  ) = {
    server
      .when(
        request()
          .withMethod("POST")
          .withPath(s"/api/sample_data/$dataType")
          .withHeader("Connection", "keep-alive")
          .withHeader("kbn-version", buildVersion)
      )
      .respond(
        response()
          .withStatusCode(200)
          .withBody(
            json(
              s"""{"elasticsearchIndicesCreated":{"kibana_sample_data_$dataType":4675},"kibanaSavedObjectsLoaded":9}"""
            )
          )
      )
  }

  def createDeleteSampleDataCallback(
      dataType: String,
      buildVersion: String
  ) = {
    server
      .when(
        request()
          .withMethod("DELETE")
          .withPath(s"/api/sample_data/$dataType")
          .withHeader("Connection", "keep-alive")
          .withHeader("kbn-version", buildVersion)
      )
      .respond(
        response()
          .withStatusCode(204)
      )
  }

  def destroy() = {
    if (server != null && server.isRunning()) {
      stopQuietly(server)
    }
  }
}
