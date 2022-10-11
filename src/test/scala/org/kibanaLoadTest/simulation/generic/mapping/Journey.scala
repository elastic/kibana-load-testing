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

  /**
    * Returns true if any stream contains auth API call, otherwise falls
    * This functions is used to check if journey does authentication itself or requires pre-generated Cookie.
    */
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
