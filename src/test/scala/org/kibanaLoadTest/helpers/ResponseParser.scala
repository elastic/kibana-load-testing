package org.kibanaLoadTest.helpers

import com.google.gson.{Gson, JsonObject}

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable.ListBuffer

object ResponseParser {
  private val RECORD_START_LINE = ">>>>>>>>>>>>>>>>>>>>>>>>>>"
  private val SPLIT_LINE = "========================="
  private val RECORD_END_LINE = "<<<<<<<<<<<<<<<<<<<<<<<<<"
  private val BODY_LINE = "body"
  private val REQUEST_BODY_REGEXP = "(?<=content=).*(?=\\})".r
  private val RESPONSE_BODY_REGEXP = "(?<=body:).*".r
  private val RESPONSE_STATUS_CODE_REGEXP = "\\d{3}".r
  private val STATUS_REGEXP = "OK|KO".r
  private val STATUS_MESSAGE_REGEXP = "(?:OK|KO).*$"
  private val ERROR_MESSAGE_REGEXP = "(?<=KO).*".r
  private var br: BufferedReader = null
  private var strLine: String = null

  def getRequests(filePath: String): ListBuffer[Response] = {
    val responseList = ListBuffer[Response]()
    val fsStream = new FileInputStream(filePath)
    br = new BufferedReader(new InputStreamReader(fsStream))
    strLine = br.readLine()
    while (strLine != null) {
      if (strLine.startsWith(RECORD_START_LINE)) {
        // >>>>>>>>>>>>>>>>>>>>>>>>>>
        br.readLine
        // Request:
        strLine = br.readLine.trim
        // login: OK
        val status = STATUS_REGEXP.findFirstIn(strLine).getOrElse("")
        val message =
          if (status == "KO")
            ERROR_MESSAGE_REGEXP.findFirstIn(strLine).getOrElse("").trim
          else ""
        val name =
          strLine
            .replaceAll(STATUS_MESSAGE_REGEXP, "")
            .trim
            .replaceAll(".$", "");
        br.readLine
        // =========================
        strLine = br.readLine
        // Session:
        var sessionValue = ""
        strLine = br.readLine
        while (strLine != SPLIT_LINE) {
          sessionValue = sessionValue + strLine.trim
          strLine = br.readLine
        }
        val userId = sessionValue.split(",")(1)
        strLine = br.readLine
        // HTTP request:
        val requestStr = br.readLine()
        // POST http://localhost:5620/internal/security/login
        val methodAndUrl = requestStr.split(" ", 2)
        val method = methodAndUrl(0)
        val url = if (methodAndUrl.length == 2) methodAndUrl(1) else ""
        strLine = br.readLine
        // headers:
        strLine = br.readLine
        val requestHeaders = getHeaders(SPLIT_LINE)
        // skip cookies
        if (strLine.startsWith("cookies")) {
          while (!strLine.startsWith("body") && strLine != SPLIT_LINE) {
            strLine = br.readLine()
          }
        }

        val requestBody =
          REQUEST_BODY_REGEXP
            .findFirstIn(getBodyString(SPLIT_LINE))
            .getOrElse("")
        // =========================
        strLine = br.readLine()
        // HTTP response:
        strLine = br.readLine()

        var responseStatus = ""
        var responseHeaders = ""
        var responseBody = ""

        if (!strLine.startsWith(RECORD_END_LINE)) {
          // status:
          responseStatus =
            RESPONSE_STATUS_CODE_REGEXP.findFirstIn(br.readLine()).getOrElse("")
          strLine = br.readLine()
          // headers:
          strLine = br.readLine()
          responseHeaders = getHeaders(RECORD_END_LINE)
          // response:
          responseBody = RESPONSE_BODY_REGEXP
            .findFirstIn(getBodyString(RECORD_END_LINE))
            .getOrElse("")
        }

        responseList += Response(
          userId,
          name,
          status,
          method,
          url,
          requestHeaders,
          requestBody,
          responseStatus,
          responseHeaders,
          responseBody,
          0L,
          0L,
          message
        )
      }
      // <<<<<<<<<<<<<<<<<<<<<<<<<
      strLine = br.readLine
    }
    fsStream.close()

    responseList
  }

  private def getBodyString(endLine: String): String = {
    var bodyString = ""
    if (strLine.startsWith(BODY_LINE)) {
      while (!strLine.startsWith(endLine)) {
        bodyString += strLine.trim
        strLine = br.readLine()
      }
    }

    // remove multiple spaces
    bodyString.replaceAll("\\s+", " ")
  }

  private def getHeaders(endLine: String): String = {
    val gson = new Gson
    val jsonObject = new JsonObject()
    while (
      !strLine.startsWith(BODY_LINE) && !strLine.startsWith(endLine) && !strLine
        .startsWith("cookies")
    ) {
      val values = strLine.trim.split(":", 2)
      if (values.length == 2) {
        jsonObject.addProperty(values(0).trim, values(1).trim)
      }
      strLine = br.readLine
    }
    gson.toJson(jsonObject)
  }
}
