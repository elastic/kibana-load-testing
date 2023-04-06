package org.kibanaLoadTest.test.mocks

import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json
import org.mockserver.stop.Stop.stopQuietly

class ESMockServer(port: Int) {
  private val server = ClientAndServer.startClientAndServer(this.port)

  def createStatusCallback() = {
    server
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

  def destroy() = {
    if (server != null && server.isRunning()) {
      stopQuietly(this.server)
    }
  }
}
