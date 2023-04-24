package org.kibanaLoadTest.test.mocks

import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.{ClearType, HttpResponse, RequestDefinition}
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpTemplate.{TemplateType, template}
import org.mockserver.model.JsonBody.json
import org.mockserver.stop.Stop.stopQuietly

class KibanaMockServer(port: Int) {
  private val config = new Configuration().logLevel("WARN")
  private val server = ClientAndServer.startClientAndServer(config, this.port)

  def addApiMockCallback(
      requestDefinition: RequestDefinition,
      response: HttpResponse
  ): Unit = {
    server
      .clear(
        requestDefinition,
        ClearType.EXPECTATIONS
      )
      .when(requestDefinition)
      .respond(response)
  }

  def createKibanaIndexPageCallback(
      version: String = "8.7.1-SNAPSHOT"
  ): Unit = {
    server
      .clear(
        request.withPath("/login").withMethod("GET"),
        ClearType.EXPECTATIONS
      ) // clear previous behaviour, if any
      .when(request.withPath("/login").withMethod("GET"))
      .respond(
        response()
          .withBody(version)
          .withStatusCode(200)
      )
  }

  def createKibanaStatusCallback(
      build_hash: String = "abcdefg",
      build_number: Long = 9007199,
      build_snapshot: Boolean = true,
      number: String = "8.7.1"
  ) = {
    server
      .clear(
        request.withPath("/api/status").withMethod("GET"),
        ClearType.EXPECTATIONS
      ) // clear previous behaviour, if any
      .when(request.withPath("/api/status").withMethod("GET"))
      .respond(
        response()
          .withStatusCode(200)
          .withBody(
            json(
              s"""{"version":{"build_hash":"$build_hash","build_number":$build_number,"build_snapshot":$build_snapshot,"number":"$number"}}"""
            )
          )
      )
  }

  def createSuccessfulLoginCallback() = {
    server
      .clear(
        request.withPath("/internal/security/login").withMethod("POST"),
        ClearType.EXPECTATIONS
      ) // clear previous behaviour, if any
      .when(
        request()
          .withMethod("POST")
          .withPath("/internal/security/login")
          .withHeader("Content-Type", "application/json")
          .withHeader("kbn-version")
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
      docsCount: Int = 4675,
      soCount: Int = 9
  ) = {
    server
      .when(
        request()
          .withMethod("POST")
          .withPath(s"/api/sample_data/$dataType")
          .withHeader("Connection", "keep-alive")
          .withHeader("kbn-version")
      )
      .respond(
        response()
          .withStatusCode(200)
          .withBody(
            json(
              s"""{"elasticsearchIndicesCreated":{"kibana_sample_data_$dataType":$docsCount},"kibanaSavedObjectsLoaded":$soCount}"""
            )
          )
      )
  }

  def createDeleteSampleDataCallback(
      dataType: String
  ) = {
    server
      .when(
        request()
          .withMethod("DELETE")
          .withPath(s"/api/sample_data/$dataType")
          .withHeader("Connection", "keep-alive")
          .withHeader("kbn-version")
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
