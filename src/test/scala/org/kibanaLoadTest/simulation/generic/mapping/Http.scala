package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol

case class Http(
    path: String,
    query: Option[String],
    headers: Map[String, String],
    method: String,
    body: Option[String],
    statusCode: Int,
    timeout: Option[Int]
)

object HttpJsonProtocol extends DefaultJsonProtocol {
  implicit val httpFormat = jsonFormat7(Http)
}
