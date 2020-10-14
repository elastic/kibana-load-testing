package org.kibanaLoadTest.test

import java.io.File
import java.nio.file.Paths

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.kibanaLoadTest.helpers.LogParser

class IngestionTest {

  val expCollectionSize = 305
  val expRequestString = "10 - login - 1601482441483 - 1601482441868 - 385 - OK"

  @Test
  def parseLogsTest(): Unit = {
    val targetPath = Paths.get("target").toAbsolutePath.normalize.toString
    val logFilePath: String = new File(
      targetPath + File.separator + "test-classes"
        + File.separator +"log"+ File.separator
        +"simulation.log"
    ).getAbsolutePath
    val requests = LogParser.getRequests(logFilePath)
    assertEquals(expCollectionSize, requests.length, "Incorrect collection size")
    assertEquals(expRequestString, requests(0).toString, "Incorrect content in first object")
  }
}
