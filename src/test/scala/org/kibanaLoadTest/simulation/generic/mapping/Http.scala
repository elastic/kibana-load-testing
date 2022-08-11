package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol

case class Http(
    path: String,
    headers: Map[String, String],
    method: String,
    body: Option[String],
    statusCode: Int
)

object HttpJsonProtocol extends DefaultJsonProtocol {
  implicit val httpFormat = jsonFormat5(Http)
}
