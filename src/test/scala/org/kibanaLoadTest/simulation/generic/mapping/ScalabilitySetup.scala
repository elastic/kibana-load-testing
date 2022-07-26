package org.kibanaLoadTest.simulation.generic.mapping

import StageJsonProtocol._
import spray.json.DefaultJsonProtocol

case class ScalabilitySetup(
    warmup: List[Step],
    test: List[Step],
    maxDuration: String
)

object ScalabilitySetupJsonProtocol extends DefaultJsonProtocol {
  implicit val scalabilityFormat = jsonFormat3(ScalabilitySetup)
}
