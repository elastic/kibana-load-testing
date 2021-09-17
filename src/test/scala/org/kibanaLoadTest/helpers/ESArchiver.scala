package org.kibanaLoadTest.helpers

import io.circe.Json

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable.ArrayBuffer
import io.circe.parser._

object ESArchiver {

  def parseFileIntoJsonArray(filePath: String): ArrayBuffer[Json] = {
    val br = new BufferedReader(
      new InputStreamReader(new FileInputStream(filePath))
    )
    var strLine = Option(br.readLine())
    var jsonString = ""
    val jsonStringBuffer = new ArrayBuffer[String]()
    while (strLine.isDefined) {
      val nextLine = Option(br.readLine())
      if (!nextLine.isDefined || nextLine.get.length == 0) {
        jsonString += strLine.get
        jsonStringBuffer += jsonString
        jsonString = ""
      } else {
        jsonString += strLine.get
      }
      strLine = nextLine
    }

    jsonStringBuffer.map(jsonString => parse(jsonString).getOrElse(Json.Null))
  }
}
