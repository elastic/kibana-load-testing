package org.kibanaLoadTest.helpers

case class Response(
    userId: String,
    name: String,
    simulation: String,
    status: String,
    method: String,
    url: String,
    requestHeaders: String,
    requestBody: String,
    responseStatus: String,
    responseHeaders: String,
    responseBody: String,
    requestSendStartTime: Long,
    responseReceiveEndTime: Long,
    message: String,
    requestTime: Long
) {
  override def toString: String =
    """$userId - $name - $status - 
      |$method - $url - $requestHeaders - $requestBody - $responseStatus - 
      |$responseHeaders - $responseBody - $requestSendStartTime - 
      |$responseReceiveEndTime - $responseReceiveEndTime - $message - 
      |$requestTime""".stripMargin
}

object Response {
  def apply(
      userId: String,
      name: String,
      simulation: String,
      status: String,
      method: String,
      url: String,
      requestHeaders: String,
      requestBody: String,
      responseStatus: String,
      responseHeaders: String,
      responseBody: String,
      requestSendStartTime: Long,
      responseReceiveEndTime: Long,
      message: String
  ): Response =
    Response(
      userId,
      name,
      simulation,
      status,
      method,
      url,
      requestHeaders,
      requestBody,
      responseStatus,
      responseHeaders,
      responseBody,
      requestSendStartTime,
      responseReceiveEndTime,
      message,
      responseReceiveEndTime - requestSendStartTime
    )
}
