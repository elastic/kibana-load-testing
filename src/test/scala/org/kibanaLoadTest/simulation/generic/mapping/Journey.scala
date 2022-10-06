package org.kibanaLoadTest.simulation.generic.mapping

import ScalabilitySetupJsonProtocol._
import TestDataJsonProtocol._
import org.kibanaLoadTest.simulation.generic.mapping.RequestStreamJsonProtocol._
import spray.json.DefaultJsonProtocol

case class Journey(
    journeyName: String,
    kibanaVersion: String,
    scalabilitySetup: ScalabilitySetup,
    testData: Option[TestData],
    streams: List[RequestStream]
) {
  def needsAuthentication(): Boolean = {
    !streams
      .map(stream =>
        stream.requests
          .map(req => req.getRequestUrl())
          .contains("/internal/security/login")
      )
      .contains(true)
  }
}

object JourneyJsonProtocol extends DefaultJsonProtocol {
  implicit val journeyFormat = jsonFormat5(Journey)
}
