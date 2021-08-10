package org.kibanaLoadTest.helpers

case class UsersCount(timestamp: String, count: Number) {
  override def toString: String = s"$timestamp - $count"
}
