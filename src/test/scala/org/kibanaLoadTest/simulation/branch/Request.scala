package org.kibanaLoadTest.simulation.branch

import spray.json.{DefaultJsonProtocol, JsNumber, JsString, JsValue, JsonFormat, deserializationError}

import java.util.Date

case class Request(path: String, headers: Map[String, String], method: String, body: Option[String], statusCode: Int, timestamp: String)

//object DateJsonProtocol extends DefaultJsonProtocol {
//  implicit object DateJsonFormat extends JsonFormat[Date] {
//    def write(d: Date) = JsNumber(d.getTime)
//
//    def read(json: JsValue) = json match {
//      case JsString(v) => new Date(v.longValue)
//      case _ => deserializationError("Number expected")
//    }
//  }
//}

//import org.kibanaLoadTest.simulation.branch.DateJsonProtocol._

object RequestJsonProtocol extends DefaultJsonProtocol {
  implicit val requestFormat = jsonFormat6(Request)
}
