package org.kibanaLoadTest.helpers

class Request {
  var name = ""
  var requestSendStartTime = 0L
  var responseReceiveEndTime = 0L
  var requestTime = 0L
  var status = ""
  var message = ""

  def this(
      name: String,
      startTime: Long,
      endTime: Long,
      status: String,
      message: String
  ) = {
    this()
    this.name = name
    this.requestSendStartTime = startTime
    this.responseReceiveEndTime = endTime
    this.requestTime = endTime - startTime
    this.status = status
    this.message = message.trim
  }

  def toJsonString: String = {
    s"""
        |{
        |"name": "${this.name}",
        |"requestSendStartTime": "${Helper.convertDateToUTC(
      this.requestSendStartTime
    )}",
        |"responseReceiveEndTime": "${Helper.convertDateToUTC(
      this.responseReceiveEndTime
    )}",
        |"status": "${this.status}",
        |"requestTime": ${this.requestTime},
        |"message": "${this.message}"
        |}
        |""".stripMargin
  }

  override def toString: String = {
    val baseStr: String =
      s"$name - $requestSendStartTime - $responseReceiveEndTime - $requestTime - $status"
    if (message.isEmpty) baseStr else s"$baseStr - $message"
  }
}
