package org.kibanaLoadTest.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.kibanaLoadTest.helpers.Version

class HelpersTest {

  @Test
  def compareVersionsTest = {
    val v1 = new Version("7.8.15")
    val v2 = new Version("7.10")
    assertEquals(v1.compareTo(v2), -1)
    val v3 = new Version("7.10.1")
    assertEquals(v3.compareTo(v2), 1)
    val v4 = new Version("7.10.2-snapshot")
    assertEquals(v4.compareTo(v3), 1)
  }

}
