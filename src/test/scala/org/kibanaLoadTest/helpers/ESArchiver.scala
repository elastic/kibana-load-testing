package org.kibanaLoadTest.helpers

import io.circe.Json
import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable.ArrayBuffer
import io.circe.parser._
import io.circe.syntax.EncoderOps
import java.util.zip.GZIPInputStream

case class Doc(_type: String, index: String, source: Json)

object ESArchiver {
  def parseFile(
      filePath: String,
      encoding: String = "UTF-8"
  ): ArrayBuffer[Json] = {
    val inputStream = if (filePath.endsWith(".gz")) {
      new InputStreamReader(
        new GZIPInputStream(new FileInputStream(filePath)),
        encoding
      )
    } else if (filePath.endsWith(".json")) {
      new InputStreamReader(new FileInputStream(filePath))
    } else throw new RuntimeException(s"Cannot parse the file ${filePath}")

    val br = new BufferedReader(inputStream)
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

  def convertToDoc(json: Json): Doc = {
    val _type = json.hcursor.get[String]("type").getOrElse(null)
    val index =
      json.hcursor.downField("value").get[String]("index").getOrElse(null)
    val value = json.hcursor.get[Json]("value").getOrElse(null)
    val source = if (_type == "doc") {
      json.hcursor.downField("value").get[Json]("source").getOrElse(null)
    } else if (_type == "index") {
      value.withObject(jsonObj => jsonObj.remove("index").asJson)
    } else {
      throw new RuntimeException(s"Unknown ${_type} type")
    }
    Doc(_type, index, source)
  }

  def readDataFromFile(
      filePath: String,
      encoding: String = "UTF-8"
  ): ArrayBuffer[Doc] = {
    val jsonArray = parseFile(filePath, encoding)
    jsonArray.map(json => convertToDoc(json))
  }
}
