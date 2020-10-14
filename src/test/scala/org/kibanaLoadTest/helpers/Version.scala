package org.kibanaLoadTest.helpers

class Version(var version: String) extends Comparable[Version] {
  if (version == null) throw new IllegalArgumentException("Version can not be null")
  if (!version.matches("[0-9]+(\\.[0-9]+)*")) throw new IllegalArgumentException("Invalid version format")

  def isAbove79x: Boolean = {
    this.compareTo(new Version("7.10")) != -1
  }

  override def compareTo(that: Version): Int = {
    if (that == null) return 1
    val thisParts = this.get.split("\\.")
    val thatParts = that.get.split("\\.")
    val length = Math.max(thisParts.length, thatParts.length)
    for (i <- 0 until length) {
      val thisPart = if (i < thisParts.length) thisParts(i).toInt
      else 0
      val thatPart = if (i < thatParts.length) thatParts(i).toInt
      else 0
      if (thisPart < thatPart) return -1
      if (thisPart > thatPart) return 1
    }
    0
  }

  final def get: String = this.version
}
