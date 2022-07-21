package org.kibanaLoadTest.simulation.branch

import spray.json.DefaultJsonProtocol

case class Step(action: String, minUsersCount: Option[Int], maxUsersCount: Int, duration: String)

object StageJsonProtocol extends DefaultJsonProtocol {
  implicit val stageFormat = jsonFormat4(Step)
}
