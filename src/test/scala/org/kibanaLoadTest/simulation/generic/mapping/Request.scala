package org.kibanaLoadTest.simulation.generic.mapping

import org.kibanaLoadTest.helpers.Helper
import spray.json.{
  DefaultJsonProtocol,
  JsNumber,
  JsString,
  JsValue,
  JsonFormat,
  RootJsonFormat,
  deserializationError
}

import java.util.Date
import org.kibanaLoadTest.simulation.generic.mapping.HttpJsonProtocol._

case class Request(
    http: Http,
    date: Date
) {
  def getRequestUrl(): String = http.path
}

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
  implicit val requestFormat: RootJsonFormat[Request] = jsonFormat2(Request)
}
