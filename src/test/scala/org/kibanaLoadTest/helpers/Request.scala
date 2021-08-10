package org.kibanaLoadTest.helpers

case class Request(
    name: String,
    requestSendStartTime: Long,
    responseReceiveEndTime: Long,
    status: String,
    message: String,
    requestTime: Long
) {
  override def toString: String = {
    val baseStr: String =
      s"$name - $requestSendStartTime - $responseReceiveEndTime - $requestTime - $status"
    if (message != null && message.length > 0) s"$baseStr - $message"
    else baseStr
  }
}

object Request {
  def apply(
      name: String,
      requestSendStartTime: Long,
      responseReceiveEndTime: Long,
      status: String,
      message: String
  ): Request =
    Request(
      name,
      requestSendStartTime,
      responseReceiveEndTime,
      status,
      message,
      responseReceiveEndTime - requestSendStartTime
    )
}
