package org.kibanaLoadTest.simulation.generic.mapping

import org.kibanaLoadTest.helpers.Helper
import spray.json.{
  DefaultJsonProtocol,
  JsNumber,
  JsString,
  JsValue,
  JsonFormat,
  deserializationError
}

import java.util.Date

case class Request(
    path: String,
    headers: Map[String, String],
    method: String,
    body: Option[String],
    statusCode: Int,
    timestamp: Date
)

object DateJsonProtocol extends DefaultJsonProtocol {
  implicit object DateJsonFormat extends JsonFormat[Date] {
    def write(d: Date) = JsNumber(d.getTime)

    def read(json: JsValue): Date =
      json match {
        case JsString(v) => Helper.convertStringToDate(v)
        case _           => deserializationError("Number expected")
      }
  }
}

import org.kibanaLoadTest.simulation.generic.mapping.DateJsonProtocol._

object RequestJsonProtocol extends DefaultJsonProtocol {
  implicit val requestFormat = jsonFormat6(Request)
}
