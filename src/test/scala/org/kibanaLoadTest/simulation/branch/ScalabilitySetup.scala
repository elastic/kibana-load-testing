package org.kibanaLoadTest.simulation.branch

import org.kibanaLoadTest.simulation.branch.StageJsonProtocol._
import spray.json.DefaultJsonProtocol

case class ScalabilitySetup(warmup: Stage, test: Stage, maxDuration: Int)


object ScalabilitySetupJsonProtocol extends DefaultJsonProtocol {
  implicit val scalabilityFormat = jsonFormat3(ScalabilitySetup)
}
