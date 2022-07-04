package org.kibanaLoadTest.simulation.branch

import org.kibanaLoadTest.simulation.branch.ScalabilitySetupJsonProtocol._
import org.kibanaLoadTest.simulation.branch.RequestJsonProtocol._
import spray.json.DefaultJsonProtocol


case class Journey(name: String, scalabilitySetup: ScalabilitySetup, requests: List[Request])


object JourneyJsonProtocol extends DefaultJsonProtocol {
  implicit val format = jsonFormat3(Journey)
}
