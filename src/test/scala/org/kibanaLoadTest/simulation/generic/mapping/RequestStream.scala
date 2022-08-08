package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol
import org.kibanaLoadTest.simulation.generic.mapping.RequestJsonProtocol._
import org.kibanaLoadTest.simulation.generic.mapping.DateJsonProtocol._

import java.util.Date

case class RequestStream(
    startTime: Date,
    endTime: Date,
    requests: List[Request]
)

object RequestStreamJsonProtocol extends DefaultJsonProtocol {
  implicit val requestStreamFormat = jsonFormat3(RequestStream)
}
