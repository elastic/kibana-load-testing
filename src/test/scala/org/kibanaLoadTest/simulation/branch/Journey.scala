package org.kibanaLoadTest.simulation.branch

import org.kibanaLoadTest.simulation.branch.ScalabilitySetupJsonProtocol._
import org.kibanaLoadTest.simulation.branch.TraceJsonProtocol._
import spray.json.DefaultJsonProtocol


case class Journey(journeyName: String, kibanaVersion: String, scalabilitySetup: ScalabilitySetup, requests: List[Trace])


object JourneyJsonProtocol extends DefaultJsonProtocol {
  implicit val journeyFormat = jsonFormat4(Journey)
}
