package org.kibanaLoadTest.simulation.generic.mapping

import ScalabilitySetupJsonProtocol._
import TraceJsonProtocol._
import spray.json.DefaultJsonProtocol

case class Journey(
    journeyName: String,
    kibanaVersion: String,
    scalabilitySetup: ScalabilitySetup,
    requests: List[Trace]
)

object JourneyJsonProtocol extends DefaultJsonProtocol {
  implicit val journeyFormat = jsonFormat4(Journey)
}
