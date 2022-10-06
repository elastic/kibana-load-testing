package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol

case class Step(
    action: String,
    userCount: Option[Int],
    minUsersCount: Option[Int],
    maxUsersCount: Option[Int],
    duration: Option[String]
) {
  def getMaxUsersCount: Int = {
    userCount match {
      case Some(value) => value
      case None =>
        maxUsersCount match {
          case Some(value) => value
          case None        => 0
        }
    }
  }
  override def toString: String = {
    val users = userCount match {
      case Some(count) => s"[$count]"
      case None =>
        maxUsersCount match {
          case Some(maxCount) =>
            minUsersCount match {
              case Some(minCount) => s"[from $minCount to $maxCount]"
              case None           => s"[$maxCount]"
            }
          case None =>
            throw new IllegalArgumentException(
              "workload model should have 'userCount' or 'maxUsersCount' defined"
            )
        }
    }

    val during = duration match {
      case Some(value) => s"during [$value]"
      case None        => ""
    }
    s"workload model=[$action] users=$users $during"
  }
}

object StepJsonProtocol extends DefaultJsonProtocol {
  implicit val stepFormat = jsonFormat5(Step)
}
