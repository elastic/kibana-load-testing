package org.kibanaLoadTest.simulation.branch

import spray.json.DefaultJsonProtocol

case class Stage(action: String, minUserCount: Option[Int], maxUserCount: Int, duration: Int)

object StageJsonProtocol extends DefaultJsonProtocol {
  implicit val stageFormat = jsonFormat4(Stage)
}
