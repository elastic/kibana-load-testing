package org.kibanaLoadTest.helpers

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable.ListBuffer

object RequestParser {
  private val RECORD_START_LINE = ">>>>>>>>>>>>>>>>>>>>>>>>>>"
  private val SPLIT_LINE = "========================="
  private val RECORD_END_LINE = "<<<<<<<<<<<<<<<<<<<<<<<<<"
  private val BODY_LINE = "body"
  private val REQUEST_BODY_REGEXP = "(?<=content=).*".r
  private val RESPONSE_BODY_REGEXP = "(?<=body:).*".r
  private val RESPONSE_STATUS_CODE_REGEXP = "\\d{3}".r
  private val STATUS_REGEXP = "OK|KO".r
  private val STATUS_MESSAGE_REGEXP = "(?:OK|KO).*$"
  private var br: BufferedReader = null
  private var strLine: String = null

  def getRequests(filePath: String): ListBuffer[GatlingRequest] = {
    val responseList = ListBuffer[GatlingRequest]()
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
        val methodAndUrl = requestStr.split(" ")
        strLine = br.readLine
        // headers:
        strLine = br.readLine
        val requestHeaders = getHeaders(SPLIT_LINE)
        val requestBody = getBodyString(SPLIT_LINE)
        // =========================
        br.readLine()
        // HTTP response:
        strLine = br.readLine()
        // status:
        val responseStatus = br.readLine()
        strLine = br.readLine()
        //headers:
        strLine = br.readLine()
        val responseHeaders = getHeaders(RECORD_END_LINE)
        val responseBody = getBodyString(RECORD_END_LINE)

        responseList += GatlingRequest(
          userId,
          name,
          status,
          sessionValue,
          methodAndUrl(0),
          methodAndUrl(1),
          requestHeaders,
          REQUEST_BODY_REGEXP.findFirstIn(requestBody).getOrElse(""),
          RESPONSE_STATUS_CODE_REGEXP.findFirstIn(responseStatus).getOrElse(""),
          responseHeaders,
          RESPONSE_BODY_REGEXP.findFirstIn(responseBody).getOrElse(""),
          0L,
          0L,
          ""
        )
      }
      strLine = br.readLine
      // <<<<<<<<<<<<<<<<<<<<<<<<<
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

    bodyString
  }

  private def getHeaders(endLine: String): String = {
    var headers = ""
    while (!strLine.startsWith(BODY_LINE) && !strLine.startsWith(endLine)) {
      if (headers.length > 0) {
        headers += " ; "
      }
      headers += strLine.trim
      strLine = br.readLine
    }

    headers
  }
}
