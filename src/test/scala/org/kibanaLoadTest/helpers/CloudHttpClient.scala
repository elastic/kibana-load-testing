package org.kibanaLoadTest.helpers

import java.time.Instant

import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}
import jodd.util.ThreadUtil.sleep
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import spray.json.lenses.JsonLenses._
import spray.json.DefaultJsonProtocol._

class CloudHttpClient {
  private var DEPLOYMENT_READY_TIMOEOUT = 5 * 60 * 1000 // 5 min
  private val DEPLOYMENT_POLLING_INTERVAL = 20 * 1000 // 20 sec
  private val httpClient = HttpClientBuilder.create.build
  private val deployPayloadTemplate = "cloudPayload/createDeployment.json"
  private val baseUrl = "https://staging.found.no/api/v1/deployments"
  private val API_KEY = Option(System.getenv("API_KEY"))
    .orElse(
      throw new RuntimeException(
        "API_KEY variable is required for new deployment"
      )
    )
    .get
  val logger: Logger = LoggerFactory.getLogger("httpClient")

  def preparePayload(stackVersion: String, config: Config): String = {
    logger.info(
      s"preparePayload: Using ${deployPayloadTemplate} payload template"
    )
    val template = Helper.loadJsonString(deployPayloadTemplate)
    logger.info(s"preparePayload: Using ${config.toString}")

    val payload = template
      .update('name ! set[String](s"load-testing-${Instant.now.toEpochMilli}"))
      .update(
        'resources / 'elasticsearch / element(
          0
        ) / 'plan / 'elasticsearch / 'version ! set[String](stackVersion)
      )
      .update(
        'resources / 'elasticsearch / element(
          0
        ) / 'plan / 'cluster_topology / element(0) / 'size / 'value
          ! set[String](config.getString("elasticsearch.deployment_template"))
      )
      .update(
        'resources / 'elasticsearch / element(
          0
        ) / 'plan / 'cluster_topology / element(0) / 'size / 'value
          ! set[Int](config.getInt("elasticsearch.memory"))
      )
      .update(
        'resources / 'kibana / element(0) / 'plan / 'kibana / 'version ! set[
          String
        ](stackVersion)
      )
      .update(
        'resources / 'kibana / element(0) / 'plan / 'cluster_topology / element(
          0
        ) / 'size / 'value
          ! set[Int](config.getInt("kibana.memory"))
      )
      .update(
        'resources / 'apm / element(0) / 'plan / 'apm / 'version ! set[String](
          stackVersion
        )
      )

    payload.toString
  }

  def createDeployment(payload: String): Map[String, String] = {
    logger.info(s"createDeployment: Creating new deployment")
    val createRequest = new HttpPost(baseUrl)
    createRequest.addHeader("Authorization", s"ApiKey ${API_KEY}")
    createRequest.setEntity(new StringEntity(payload))
    val response = httpClient.execute(createRequest)
    val responseString = EntityUtils.toString(response.getEntity)
    val meta = Map(
      "deploymentId" -> responseString.extract[String]('id),
      "username" -> responseString
        .extract[String]('resources / element(0) / 'credentials / 'username),
      "password" -> responseString.extract[String](
        'resources / element(0) / 'credentials / 'password
      )
    )

    logger.info(
      s"createDeployment: deployment ${meta("deploymentId")} is created"
    )

    meta
  }

  def getDeploymentStateInfo(id: String): String = {
    val getStateRequest = new HttpGet(s"${baseUrl}/${id}")
    getStateRequest.addHeader("Authorization", s"ApiKey ${API_KEY}")
    val response = httpClient.execute(getStateRequest)
    EntityUtils.toString(response.getEntity)
  }

  def getInstanceStatus(deploymentId: String): Map[String, String] = {
    val jsonString = getDeploymentStateInfo(deploymentId)
    val items = Array("kibana", "elasticsearch", "apm")

    items
      .map(item => {
        val status = jsonString
          .extract[String]('resources / item / element(0) / 'info / 'status)
        item -> status
      })
      .toMap
  }

  def getKibanaUrl(deploymentId: String): String = {
    val jsonString = getDeploymentStateInfo(deploymentId)
    jsonString.extract[String](
      'resources / 'kibana / element(0) / 'info / 'metadata / 'service_url
    )
  }

  def waitForClusterToStart(deploymentId: String) = {
    var started = false
    var waitTime = DEPLOYMENT_READY_TIMOEOUT
    var poolingInterval = DEPLOYMENT_POLLING_INTERVAL
    logger.info(
      s"waitForClusterToStart: waitTime ${waitTime}ms, poolingInterval ${poolingInterval}ms"
    )
    while (!started && poolingInterval > 0) {
      var statuses = Map.empty[String, String]
      try {
        statuses = getInstanceStatus(deploymentId)
      } catch {
        case ex: Exception =>
          logger.error(ex.getMessage)
      }
      if (
        !statuses.isEmpty && statuses.values
          .filter(s => s != "started")
          .size == 0
      ) {
        logger.info(s"waitForClusterToStart: Deployment is ready!")
        started = true
      } else {
        logger.info(
          s"waitForClusterToStart: Deployment is in progress... ${statuses.toString()}"
        )
        waitTime -= poolingInterval
        sleep(poolingInterval)
      }
    }

    if (!started) {
      throw new RuntimeException(
        s"Deployment ${deploymentId} was not ready after ${waitTime} ms"
      )
    }
  }

  def deleteDeployment(id: String): Unit = {
    logger.info(s"deleteDeployment: Deployment ${id}")
    val deleteRequest = new HttpPost(
      baseUrl + s"/${id}/_shutdown?hide=true&skip_snapshot=true"
    )
    deleteRequest.addHeader("Authorization", s"ApiKey ${API_KEY}")
    val response = httpClient.execute(deleteRequest)
    logger.info(
      s"deleteDeployment: Finished with status code ${response.getStatusLine.getStatusCode}"
    )
  }

}
