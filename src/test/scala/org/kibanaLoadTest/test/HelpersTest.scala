package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.Test
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.getTargetPath
import org.kibanaLoadTest.helpers.{Helper, Version}

import java.io.File

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
        getClass.getResource("/test/lastDeployment.txt").getPath
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
      getTargetPath + File.separator + "gatling" + File.separator + "demo1",
      getTargetPath + File.separator + "gatling" + File.separator + "demo2"
    )
    testFolders.foreach(path => new File(path).mkdir())

    val paths = Helper.getReportFolderPaths
    assertEquals(2, paths.length)
  }
}
