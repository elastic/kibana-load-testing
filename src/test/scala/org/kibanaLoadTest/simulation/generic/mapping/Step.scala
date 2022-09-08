package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol

case class Step(
    action: String,
    minUsersCount: Option[Int],
    maxUsersCount: Int,
    duration: String
)

object StepJsonProtocol extends DefaultJsonProtocol {
  implicit val stepFormat = jsonFormat4(Step)
}
