package org.kibanaLoadTest.simulation.generic.mapping

import StepJsonProtocol._
import spray.json.DefaultJsonProtocol

case class ScalabilitySetup(
    warmup: List[Step],
    test: List[Step],
    maxDuration: String
) {
  def getMaxConcurrentUsers(): Int =
    (this.warmup ::: this.test).map(step => step.getMaxUsersCount).max
}

object ScalabilitySetupJsonProtocol extends DefaultJsonProtocol {
  implicit val scalabilityFormat = jsonFormat3(ScalabilitySetup)
}
