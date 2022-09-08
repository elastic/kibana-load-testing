package org.kibanaLoadTest.simulation.generic.mapping

import spray.json.DefaultJsonProtocol

case class TestData(
    kbnArchives: Option[Array[String]],
    esArchives: Option[Array[String]]
)

object TestDataJsonProtocol extends DefaultJsonProtocol {
  implicit val testDataFormat = jsonFormat2(TestData)
}
