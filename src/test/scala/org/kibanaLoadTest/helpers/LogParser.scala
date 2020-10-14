package org.kibanaLoadTest.helpers

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable.ListBuffer

object LogParser {

  def getRequests(filePath: String): List[Request] = {
    var requests = new ListBuffer[Request]()
    val fsStream = new FileInputStream(filePath)
    val br = new BufferedReader(new InputStreamReader(fsStream))
    var strLine = br.readLine
    while (strLine != null) {
      /* collect only lines starting with REQUEST */
      // [REQUEST	10		login	1601482441483	1601482441868	OK	]
      // [REQUEST	3		get bootstrap.js	1601482441914	1601482441965	KO	regex(\/(.*)',).findAll.exists, found nothing]
      if (strLine.startsWith("REQUEST")) {
        val values = strLine.split("\t")
        // [REQUEST, USER_ID, <empty>, NAME, REQUEST_FIRST_BYTE_TIME, RESPONSE_LAST_BYTE_TIME, STATUS, MESSAGE]
        requests += new Request(Integer.valueOf(values(1)), values(3), values(4).toLong, values(5).toLong, values(6), values(7))
      }
      strLine = br.readLine
    }
    fsStream.close()

    requests.toList;
  }
}
