package org.kibanaLoadTest.simulation.branch

import spray.json.DefaultJsonProtocol
import org.kibanaLoadTest.simulation.branch.RequestJsonProtocol._

case class Trace (traceId: String, request: Request)

object TraceJsonProtocol extends DefaultJsonProtocol {
  implicit val traceFormat = jsonFormat2(Trace)
}
