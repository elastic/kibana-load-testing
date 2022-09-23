package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol

case class Step(
    action: String,
    minUsersCount: Option[Int],
    maxUsersCount: Int,
    duration: String
) {
  override def toString: String =
    s"action=[$action] users=[${if (minUsersCount.isDefined) s"${minUsersCount.get},"
    else ""}$maxUsersCount] duration=[$duration]"
}

object StepJsonProtocol extends DefaultJsonProtocol {
  implicit val stepFormat = jsonFormat4(Step)
}
