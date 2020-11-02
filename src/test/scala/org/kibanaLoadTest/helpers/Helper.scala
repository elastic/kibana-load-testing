package org.kibanaLoadTest.helpers

import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.{Calendar, Date, TimeZone}

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

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
    dtf.format(c.getTime().toInstant)
  }

  def convertDateToUTC(timestamp: Long): String = {
    val sdf = new SimpleDateFormat(dateFormat)
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(new Date(timestamp))
  }

  def readResourceConfigFile(configName: String): Config = {
    val is = getClass.getClassLoader.getResourceAsStream(configName)
    val source = scala.io.Source.fromInputStream(is).mkString
    ConfigFactory.parseString(source)
  }

  def loadJsonString(filePath: String): String = {
    Source
      .fromURL(getClass.getClassLoader.getResource(filePath))
      .getLines
      .mkString
  }

  def loadKibanaConfig(configName: String): Object = {
    val is = getClass.getClassLoader.getResourceAsStream(configName)
    val source = scala.io.Source.fromInputStream(is).mkString
    val config = ConfigFactory.parseString(source)

    object appConfig {
      val baseUrl = config.getString("app.host")
      val buildVersion = config.getString("app.version")
      val isSecurityEnabled = config.getBoolean("security.on")
      val loginPayload = config.getString("auth.loginPayload")
    }

    appConfig
  }

  def getLastReportPath(): String = {
    val targetPath = Paths.get("target").toAbsolutePath.normalize.toString
    val dir: File = new File(targetPath + File.separator + "gatling")
    val files: Array[File] = dir.listFiles
    files.toList
      .filter(file => file.isDirectory)
      .maxBy(file => file.lastModified())
      .getAbsolutePath
  }
}
