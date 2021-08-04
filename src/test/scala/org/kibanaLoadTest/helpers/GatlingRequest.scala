package org.kibanaLoadTest.helpers

case class GatlingRequest(
    userId: String,
    name: String,
    status: String,
    session: String,
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
)
object GatlingRequest {
  def apply(
      userId: String,
      name: String,
      status: String,
      session: String,
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
  ): GatlingRequest =
    GatlingRequest(
      userId,
      name,
      status,
      session,
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
