package org.kibanaLoadTest.helpers

class Request {
  var userId = 0;
  var name = "";
  var requestSendStartTime = 0L;
  var responseReceiveEndTime = 0L;
  var requestTime = 0L;
  var status = "";
  var message = "";

  def this(
      userId: Int,
      name: String,
      startTime: Long,
      endTime: Long,
      status: String,
      message: String
  ) {
    this()
    this.userId = userId
    this.name = name
    this.requestSendStartTime = startTime
    this.responseReceiveEndTime = endTime
    this.requestTime = endTime - startTime
    this.status = status
    this.message = message.trim
  }

  override def toString: String = {
    val baseStr =
      s"""${userId} - ${name} - ${requestSendStartTime} - ${responseReceiveEndTime} - ${requestTime} - ${status}"""
    if (message.length == 0) baseStr else s"""${baseStr} - ${message}"""
  }
}
