package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.Test
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.{generateUUID, getTargetPath}
import org.kibanaLoadTest.helpers.{Helper, LogParser, RequestParser, Version}

import java.io.File
import java.util.Calendar
import scala.reflect.io.Directory

class HelpersTest {

  @Test
  def compareVersionsTest(): Unit = {
    val v1 = new Version("7.8.15")
    val v2 = new Version("7.10")
    assertEquals(v1.compareTo(v2), -1)
    val v3 = new Version("7.10.1")
    assertEquals(v3.compareTo(v2), 1)
    val v4 = new Version("7.10.2-snapshot")
    assertEquals(v4.compareTo(v3), 1)
  }

  @Test
  def createKibanaConfigTest(): Unit = {
    val config = new KibanaConfiguration(
      Helper.readResourceConfigFile("config/local.conf")
    )
    assertEquals(config.baseUrl, "http://localhost:5620")
    assertEquals(config.buildVersion, "8.0.0")
    assertEquals(config.isAbove79x, true)
    assertEquals(config.isSecurityEnabled, true)
    assertEquals(config.loginStatusCode, 200)
    assertEquals(
      config.loginPayload,
      """{"providerType":"basic","providerName":"basic","currentURL":"http://localhost:5620/login","params":{"username":"elastic","password":"changeme"}}"""
    )
    assertEquals(config.deploymentId, None)
  }

  @Test
  def validateUrlThrowsExceptionTest(): Unit = {

    val exceptionThatWasThrown = assertThrows(
      classOf[RuntimeException],
      () => {
        def foo() = Helper.validateUrl("localhost", "Bad Url string")

        foo()
      }
    )

    assertEquals(
      "Bad Url string\n no protocol: localhost",
      exceptionThatWasThrown.getMessage
    )
  }

  @Test
  def validateUrlCorrectsStringTest(): Unit = {
    val validUrl =
      "https://4c976c670a125.us-central1.gcp.server.no:9243"

    assertEquals(
      validUrl,
      Helper.validateUrl(validUrl, "Smth went wrong")
    )
  }

  @Test
  def readFileToMapTest(): Unit = {
    val data =
      Helper.readFileToMap(
        getClass.getResource("/test/lastRun.txt").getPath
      )

    assertEquals(
      Map(
        "branch" -> "",
        "deploymentId" -> "asdkjqwr9cuw4j23k",
        "version" -> "8.0.0",
        "baseUrl" -> "http://localhost:5620",
        "buildNumber" -> "59211"
      ),
      data
    )
  }

  @Test
  def getReportFolderPathsTest(): Unit = {
    val testFolders = List(
      getTargetPath + File.separator + "gatling",
      getTargetPath + File.separator + "gatling" + File.separator + "_test_folder1",
      getTargetPath + File.separator + "gatling" + File.separator + "_test_folder2"
    )
    testFolders.foreach(path => new File(path).mkdir())

    val paths = Helper.getReportFolderPaths
    val directory = new Directory(
      new File(getTargetPath + File.separator + "gatling")
    )
    directory.deleteRecursively()
    assertEquals(2, paths.length)
  }

  @Test
  def updateTimeValuesTest(): Unit = {
    val testStr =
      """
        |{
        |   "range":{
        |      "order_date":{
        |         "gte":"2019-03-20T17:49:18.848Z",
        |         "lte":"2019-04-22T17:49:18.851Z",
        |         "format":"strict_date_optional_time"
        |      }
        |   },
        |   "sessionId":"3e0ee321-2b66-4da9-a956-23a9fbf07289",
        |   "track_total_hits":false
        |}
        |""".stripMargin
    val start = Helper.getDate(Calendar.DAY_OF_MONTH, -7)
    val end = Helper.getDate(Calendar.DAY_OF_MONTH, 0)
    val id = "4v0ee521-2v66-45a9-a956-56a9fbf072d9"
    val result =
      Helper.updateValues(
        testStr,
        Map("gte" -> start, "lte" -> end, "sessionId" -> id)
      )
    assertEquals(testStr.replaceAll("\\s+", "").length, result.length)
    assertTrue(
      result.contains(start) && result.contains(end) && result.contains(id)
    )
  }

  @Test
  def generateUUIDTest(): Unit = {
    val uuid = generateUUID
    assertTrue(uuid.matches("[a-zA-Z0-9-]+"))
  }

  @Test
  def testParser(): Unit = {
    val path =
      getTargetPath + File.separator + "responses-20210803225521.log" // "responses-20210803104459.log"
    val path2 =
      getTargetPath + File.separator + "gatling" + File.separator + "demojourney-20210803205600085" /*"demojourney-20210803084538127"*/ + File.separator + "simulation.log"
    val responseList = RequestParser.getRequests(path)
    val requests = LogParser.getRequestTimeline(path2)
    for (i <- 0 to responseList.length - 1) {
      val value = responseList(i)
      responseList(i) = value.copy(
        requestSendStartTime = requests(i).requestSendStartTime,
        responseReceiveEndTime = requests(i).responseReceiveEndTime,
        message = requests(i).message,
        requestTime = requests(i).requestTime
      )
    }
    println("test");
  }
}
