package org.kibanaLoadTest.simulation.generic.mapping

import org.kibanaLoadTest.simulation.generic.mapping.RequestJsonProtocol._
import spray.json.DefaultJsonProtocol

case class Trace(traceId: String, request: Request)

object TraceJsonProtocol extends DefaultJsonProtocol {
  implicit val traceFormat = jsonFormat2(Trace)
}
