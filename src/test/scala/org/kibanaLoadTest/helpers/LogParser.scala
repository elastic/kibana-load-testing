package org.kibanaLoadTest.helpers

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

object LogParser {

  def parseSimulationLog(
      filePath: String
  ): (Array[Request], Array[UsersCount]) = {
    val requests = new ArrayBuffer[Request]()
    val concurrentUsersMap = mutable.SortedMap[String, Number]()
    val fsStream = new FileInputStream(filePath)
    val br = new BufferedReader(new InputStreamReader(fsStream))
    var strLine = br.readLine
    var usersCount = 0
    while (strLine != null) {
      /* collect only lines starting with REQUEST */
      // [REQUEST	10		login	1601482441483	1601482441868	OK	]
      // [REQUEST	3		get bootstrap.js	1601482441914	1601482441965	KO	regex(\/(.*)',).findAll.exists, found nothing]
      if (strLine.startsWith("REQUEST")) {
        val values = strLine.replaceAll("[\\t]{2,}", "\t").split("\t")
        // [REQUEST, NAME, REQUEST_FIRST_BYTE_TIME, RESPONSE_LAST_BYTE_TIME, STATUS, MESSAGE]
        requests += Request(
          values(1),
          values(2).toLong,
          values(3).toLong,
          values(4),
          if (values.length == 6) values(5) else ""
        )
      } else if (strLine.startsWith("USER")) {
        val statePattern = "START|END".r
        val state: String = statePattern.findFirstIn(strLine).get
        usersCount = if (state == "START") usersCount + 1 else usersCount - 1
        val timeStampEndPattern = "\\d{13,}$".r
        val timestamp: String =
          timeStampEndPattern.findFirstIn(strLine).getOrElse("")
        concurrentUsersMap += (timestamp -> usersCount)
      }
      strLine = br.readLine
    }
    fsStream.close()

    (
      requests.toArray[Request],
      concurrentUsersMap
        .map { case (k, v) => UsersCount(k, v) }
        .toArray[UsersCount]
    )
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
