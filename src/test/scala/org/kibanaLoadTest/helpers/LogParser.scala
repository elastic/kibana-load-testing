package org.kibanaLoadTest.helpers

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable.ListBuffer

object LogParser {

  def getRequestTimeline(filePath: String): List[Request] = {
    val requests = new ListBuffer[Request]()
    val fsStream = new FileInputStream(filePath)
    val br = new BufferedReader(new InputStreamReader(fsStream))
    var strLine = br.readLine
    while (strLine != null) {
      /* collect only lines starting with REQUEST */
      // [REQUEST	10		login	1601482441483	1601482441868	OK	]
      // [REQUEST	3		get bootstrap.js	1601482441914	1601482441965	KO	regex(\/(.*)',).findAll.exists, found nothing]
      if (strLine.startsWith("REQUEST")) {
        val values = strLine.replaceAll("[\\t]{2,}", "\t").split("\t")
        // [REQUEST, NAME, REQUEST_FIRST_BYTE_TIME, RESPONSE_LAST_BYTE_TIME, STATUS, MESSAGE]
        requests += new Request(
          values(1),
          values(2).toLong,
          values(3).toLong,
          values(4),
          values(5)
        )
      }
      strLine = br.readLine
    }
    fsStream.close()

    requests.toList
  }

  def getSimulationClass(filePath: String): String = {
    val fsStream = new FileInputStream(filePath)
    val br = new BufferedReader(new InputStreamReader(fsStream))
    val strLine = br.readLine
    fsStream.close()
    // [RUN	org.kibanaLoadTest.simulation.branch.DemoJourney	demojourney	1605016152595	 	3.3.1]
    val values = strLine.split("\t")
    values(1)
  }
}
