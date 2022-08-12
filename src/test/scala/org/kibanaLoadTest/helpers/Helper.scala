package org.kibanaLoadTest.helpers

import com.google.gson.Gson

import java.io.{File, PrintWriter}
import java.net.{MalformedURLException, URL}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.{Calendar, Date, TimeZone}
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.circe.parser.parse
import org.slf4j.{Logger, LoggerFactory}
import spray.json.JsonParser
import spray.json.JsonParser.ParsingException

import scala.collection.parallel.CollectionConverters.{ArrayIsParallelizable}
import scala.jdk.CollectionConverters._
import scala.io.Source

object Helper {
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  val logger: Logger = LoggerFactory.getLogger("Helper")

  def getDate(fieldNumber: Int, daysShift: Int): String = {
    val c: Calendar = Calendar.getInstance
    c.add(fieldNumber, daysShift)
    val dtf = DateTimeFormatter
      .ofPattern(dateFormat)
      .withZone(ZoneId.systemDefault())
    dtf.format(c.getTime.toInstant)
  }

  def getMonthStartDate(monthShift: Int = 0): String = {
    val c: Calendar = Calendar.getInstance
    c.add(Calendar.DAY_OF_MONTH, 0)
    while (c.get(Calendar.DATE) > 1) {
      c.add(Calendar.DATE, -1); // Substract 1 day until first day of month.
    }
    c.add(Calendar.MONTH, monthShift)
    val dtf = DateTimeFormatter
      .ofPattern(dateFormat)
      .withZone(ZoneId.systemDefault())
    dtf.format(c.getTime.toInstant)
  }

  def convertDateToUTC(timestamp: Long): String = {
    val sdf = new SimpleDateFormat(dateFormat)
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(new Date(timestamp))
  }

  def convertStringToDate(date: String): Date = {
    val sdf = new SimpleDateFormat(dateFormat)
    sdf.parse(date)
  }

  def readResourceConfigFile(configName: String): Config = {
    val is = getClass.getClassLoader.getResourceAsStream(configName)
    if (is == null) {
      throw new RuntimeException(s"Config file is not found: $configName")
    }
    val source = scala.io.Source.fromInputStream(is).mkString
    ConfigFactory.parseString(source)
  }

  def loadJsonString(filePath: String): String = {
    val url = getClass.getClassLoader.getResource(filePath)
    if (url == null) {
      throw new RuntimeException(s"File is not found: $filePath")
    }
    val source = Source.fromURL(url)
    try source.getLines().mkString
    finally source.close()
  }

  def getTargetPath: String =
    Paths.get("target").toAbsolutePath.normalize.toString

  def getSrcPath: String =
    Paths.get("src").toAbsolutePath.normalize.toString

  def getLastReportPath: String = {
    val dir: File = new File(getTargetPath + File.separator + "gatling")
    if (!dir.exists()) {
      return null
    }
    val files: Array[File] = dir.listFiles
    files.toList
      .filter(file => file.isDirectory)
      .maxBy(file => file.lastModified())
      .getAbsolutePath
  }

  def getReportFolderPaths: Array[String] = {
    val dir: File = new File(getTargetPath + File.separator + "gatling")
    if (!dir.exists()) {
      return Array.empty
    }
    val files: Array[File] = dir.listFiles
    files
      .filter(file => file.isDirectory)
      .map(file => file.getAbsolutePath)
  }

  def getTargetFiles: List[String] = {
    val dir: File = new File(getTargetPath)
    if (!dir.exists()) {
      return List.empty
    }
    val files: Array[File] = dir.listFiles
    files.toList.map(file => file.getAbsolutePath)
  }

  def validateUrl(
      str: String,
      errorMsg: String,
      checkPort: Boolean = true
  ): String = {
    try {
      val url = new URL(str)
      url.toURI
      if (checkPort) {
        url.getPort
      }
      str.replaceAll("/$", "")
    } catch {
      case ex: MalformedURLException =>
        throw new RuntimeException(s"$errorMsg\n ${ex.getMessage}")
      case ex: Exception =>
        throw new RuntimeException(s"Unknown error ${ex.getMessage}")
    }
  }

  def writeMapToFile(data: Map[String, Any], filePath: String): Unit = {
    new PrintWriter(filePath) {
      data.foreach {
        case (k, v) =>
          write(k + "=" + v)
          write("\n")
      }
      close()
    }
  }

  def readFileToMap(filePath: String): Map[String, Any] = {
    val source = Source.fromFile(filePath)
    try source
      .getLines()
      .filter(str => str.trim.nonEmpty)
      .map(str =>
        (
          str.split("=", 2)(0),
          if (str.split("=", 2).length > 1) str.split("=", 2)(1) else ""
        )
      )
      .toMap
    finally source.close()
  }

  def getCIMeta: Map[String, Any] = {
    Map(
      "CI_BUILD_ID" -> Option(System.getenv("BUILD_ID")).getOrElse(""),
      "CI_BUILD_URL" -> Option(System.getenv("BUILD_URL")).getOrElse(""),
      "kibanaBranch" -> Option(System.getenv("KIBANA_BRANCH"))
        .getOrElse(""),
      "branch" -> Option(System.getenv("branch_specifier")).getOrElse("")
    )
  }

  def isValidJson(str: String): Boolean = {
    try {
      JsonParser(str)
      true
    } catch {
      case e: ParsingException =>
        logger.error(s"isValidJson: Failed to parse string ${e.getMessage}")
        false
    }
  }

  def getRandomNumber(min: Int, max: Int): Int =
    ((Math.random * (max - min)) + min).toInt

  def getMetaJson(testRunFilePath: String, simLogFilePath: String): Json = {
    val meta = readFileToMap(testRunFilePath) ++ Map(
      "scenario" -> LogParser
        .getSimulationClass(simLogFilePath),
      "timestamp" -> convertDateToUTC(Instant.now.toEpochMilli)
    )
    parse(new Gson().toJson(meta.asJava)).getOrElse(Json.Null)
  }

  def updateValues(str: String, kv: Map[String, String]): String = {
    val timestampRegExp = "[0-9T:.-]+Z"
    val alphaNumericRegExp = "[a-zA-Z0-9-]+"
    def expressionStr(key: String, exp: String) =
      "(?<=\"" + key + "\":\")(" + exp + ")"

    def findExpression(value: String): Option[String] = {
      if (value.matches(timestampRegExp)) Option.apply(timestampRegExp)
      else if (value.matches(alphaNumericRegExp))
        Option.apply(alphaNumericRegExp)
      else None
    }

    var result = str.replaceAll("\\s+", "")
    for ((key, value) <- kv) {
      val expr = findExpression(value)
      if (expr.isEmpty) logger.error(s"'$value' does not match any pattern")
      else
        result = result.replaceAll(
          expressionStr(key, expr.get),
          value
        )
    }
    result
  }

  import java.util.UUID

  def generateUUID: String = {
    UUID.randomUUID.toString
  }

  def prepareDocsForIngestion(
      statsFilePath: String,
      simLogFilePath: String,
      testRunFilePath: String
  ): (Array[Json], Array[Json], Array[Json]) = {
    val statsJsonString =
      GatlingStats.toJsonString(
        Source.fromFile(statsFilePath).getLines().mkString
      )
    val statsJson = parse(statsJsonString).getOrElse(Json.Null)
    val (requests, concurrentUsers, usersStats) =
      LogParser.parseSimulationLog(simLogFilePath)

    val metaJson = Helper.getMetaJson(testRunFilePath, simLogFilePath)
    val usersStatsJsonString: String = s"""
      {
      "totalUsersCount": ${usersStats.count},
      "avgUserSessionTime": ${usersStats.avgSessionTime}
      }
    """
    // final Json objects to ingest
    val combinedStatsJson = statsJson
      .deepMerge(metaJson)
      .deepMerge(parse(usersStatsJsonString).getOrElse(Json.Null))
    val requestJsonArray = requests.par
      .map(request => {
        val gson = new Gson
        val requestJson = parse(gson.toJson(request)).getOrElse(Json.Null)
        if (requestJson == Json.Null) {
          logger.error(s"Failed to parse json: ${request.toString}")
        }
        requestJson.deepMerge(metaJson)
      })
      .toArray

    val concurrentUsersJsonArray = concurrentUsers.map(stat => {
      val gson = new Gson
      val json = parse(gson.toJson(stat)).getOrElse(Json.Null)
      json.deepMerge(metaJson)
    })

    (requestJsonArray, concurrentUsersJsonArray, Array(combinedStatsJson))
  }

  def loadJsonFile(systemPropName: String): File = {
    val file = Option(System.getProperty(systemPropName)) match {
      case Some(v) => new File(v)
      case _ =>
        throw new IllegalArgumentException(
          s"The $systemPropName system property is mandatory but no value is provided"
        )
    }
    if (!file.isFile || !file.getName.endsWith(".json")) {
      throw new IllegalArgumentException(
        s"Provide path to valid json file using '$systemPropName' system property, found '$file'"
      )
    }
    file
  }
}
