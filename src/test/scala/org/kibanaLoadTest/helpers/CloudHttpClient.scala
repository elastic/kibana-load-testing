package org.kibanaLoadTest.helpers

import java.time.Instant
import com.typesafe.config.{Config, ConfigValue, ConfigValueType}
import org.slf4j.{Logger, LoggerFactory}
import jodd.util.ThreadUtil.sleep
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost}
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils

import scala.jdk.CollectionConverters.SetHasAsScala
import spray.json.lenses.JsonLenses._
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable.ListBuffer

case class DeploymentInfo(
    id: String,
    username: String,
    password: String,
    resources: List[String]
)

class CloudHttpClient(var env: CloudEnv.Value = CloudEnv.STAGING) {
  private val DEPLOYMENT_READY_TIMEOUT = 7 * 60 * 1000 // 7 min
  private val DEPLOYMENT_POLLING_INTERVAL = 30 * 1000 // 20 sec
  private val CONNECT_TIMEOUT = 30000
  private val CONNECTION_REQUEST_TIMEOUT = 60000
  private val SOCKET_TIMEOUT = 60000
  private val MSG_NO_RESPONSE =
    "Failed to create new deployment, response is not a JSON"
  private val STAGING_URL = "https://staging.found.no/api/v1"
  private val PROD_URL = "https://cloud.elastic.co/api/v1"
  private val deployPayloadTemplate = "cloudPayload/createDeployment.json"
  private val deployWithAPMPayloadTemplate =
    "cloudPayload/createExtendedDeployment.json"
  private val searchDeploymentsTemplate = "cloudPayload/searchDeployments.json"
  private val apiKey = Option(System.getenv("API_KEY"))
  private val baseUrl = if (env == CloudEnv.PROD) PROD_URL else STAGING_URL

  val logger: Logger = LoggerFactory.getLogger("httpClient")

  case class CloudResponse(statusCode: Int, reason: String, jsonString: String)

  def getEnv: CloudEnv.Value = env

  def httpBase(
      fx: CloseableHttpClient => CloseableHttpResponse
  ): Option[CloudResponse] = {
    var httpClient: CloseableHttpClient = null
    var response: CloseableHttpResponse = null
    try {
      val config = RequestConfig.custom
        .setConnectTimeout(CONNECT_TIMEOUT)
        .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
        .setSocketTimeout(SOCKET_TIMEOUT)
        .build
      httpClient =
        HttpClientBuilder.create.setDefaultRequestConfig(config).build
      response = fx(httpClient)
      Option(
        CloudResponse(
          response.getStatusLine.getStatusCode,
          response.getStatusLine.getReasonPhrase,
          EntityUtils.toString(response.getEntity)
        )
      )
    } catch {
      case e: Exception =>
        e.printStackTrace()
        Option.empty
    } finally {
      if (response != null) response.close()
      if (httpClient != null) httpClient.close()
    }
  }

  def httpGet(path: String): Option[CloudResponse] = {
    def getCall(httpClient: CloseableHttpClient): CloseableHttpResponse = {
      val httpGet: HttpGet = new HttpGet(baseUrl + path)
      httpGet.addHeader("Authorization", s"ApiKey $getApiKey")
      httpClient.execute(httpGet)
    }
    httpBase(getCall)
  }

  def httpPost(path: String, body: String = null): Option[CloudResponse] = {
    def postCall(httpClient: CloseableHttpClient): CloseableHttpResponse = {
      val httpPost: HttpPost = new HttpPost(baseUrl + path)
      httpPost.addHeader("Authorization", s"ApiKey $getApiKey")
      httpPost.addHeader("Content-Type", "application/json")
      if (body != null) httpPost.setEntity(new StringEntity(body))
      httpClient.execute(httpPost)
    }
    httpBase(postCall)
  }

  def getApiKey: String = {
    if (this.apiKey.isEmpty) {
      throw new RuntimeException(
        "API_KEY variable is required to interact with Cloud service"
      )
    } else this.apiKey.get
  }

  def setValue(
      config: Config,
      key: String,
      value: ConfigValue
  ): spray.json.lenses.Operation = {
    if (value.valueType() == ConfigValueType.STRING) {
      set[String](config.getString(key))
    } else if (value.valueType() == ConfigValueType.NUMBER) {
      set[Int](config.getInt(key))
    } else set[Boolean](config.getBoolean(key))
  }

  def preparePayload(stackVersion: String, config: Config): String = {
    val resources = ListBuffer("elasticsearch", "kibana")

    if (config.hasPath("apm")) resources += "apm"
    if (config.hasPath("enterprise_search"))
      throw new RuntimeException(
        "'enterprise_search' is not supported at the moment"
      )
    val templatePath: String =
      if (config.hasPath("apm")) deployWithAPMPayloadTemplate
      else deployPayloadTemplate

    logger.info(
      s"preparePayload: Using $templatePath payload template"
    )
    val template = Helper.loadJsonString(templatePath)
    logger.info(
      s"preparePayload: Stack version $stackVersion with ${config.toString} configuration"
    )

    val esNodesId =
      Array("coordinating", "hot_content", "warm", "cold", "frozen", "master")

    var payload = template.update(
      Symbol("name") ! set[String](s"load-testing-${Instant.now.toEpochMilli}")
    )
    // Always update stack version
    resources.foreach(resource => {
      val basePath =
        Symbol("resources") / Symbol(resource) / element(0) / Symbol("plan")
      payload = payload.update(
        basePath / Symbol(resource) / Symbol("version") ! set[String](
          stackVersion
        )
      )

      if (
        resource == "elasticsearch" && config
          .hasPath("elasticsearch.deployment_template")
      ) {
        payload = payload.update(
          basePath / Symbol("deployment_template") / Symbol("id") ! set[String](
            config.getString("elasticsearch.deployment_template")
          )
        )
      }
      if (
        resource == "elasticsearch" && config
          .hasPath("elasticsearch.autoscaling_enabled")
      ) {
        payload = payload.update(
          basePath / Symbol("autoscaling_enabled") ! set[Boolean](
            config.getBoolean("elasticsearch.autoscaling_enabled")
          )
        )
      }
    })

    // Nodes setup
    resources.foreach(res => {
      val basePath =
        Symbol("resources") / Symbol(res) / element(0) / Symbol(
          "plan"
        ) / Symbol("cluster_topology")
      if (res == "elasticsearch") {
        esNodesId.foreach(id => {
          if (config.hasPath(s"elasticsearch.$id")) {
            val path = basePath / filter("id".is[String](_ == id))
            config
              .getObject(s"elasticsearch.$id")
              .entrySet()
              .forEach(entry => {
                payload = payload.update(
                  path / (if (entry.getKey == "memory")
                            Symbol("size") / Symbol("value")
                          else Symbol(entry.getKey)) ! setValue(
                    config,
                    s"elasticsearch.$id.${entry.getKey}",
                    entry.getValue
                  )
                )
              })
          }
        })
      } else {
        if (config.hasPath(res)) {
          config
            .getObject(res)
            .entrySet()
            .forEach(entry => {
              payload = payload.update(
                basePath / element(0) / (if (entry.getKey == "memory")
                                           Symbol("size") / Symbol("value")
                                         else Symbol(entry.getKey)) ! setValue(
                  config,
                  s"$res.${entry.getKey}",
                  entry.getValue
                )
              )
            })
        }
      }
    })

    def getNestedMap(basePath: String): Map[String, JsValue] = {
      val nestedObj = config.getObject(basePath).entrySet()
      var result = Map[String, JsValue]()
      for (configPair <- SetHasAsScala(nestedObj).asScala) {
        val configName = configPair.getKey
        val configValue = configPair.getValue
        val fullPath = s"$basePath.$configName"

        if (configValue.valueType() == ConfigValueType.BOOLEAN) {
          result += (configName -> JsBoolean(config.getBoolean(fullPath)))
        } else if (configValue.valueType() == ConfigValueType.STRING) {
          result += (configName -> JsString(config.getString(fullPath)))
        } else if (configValue.valueType() == ConfigValueType.NUMBER) {
          result += (configName -> JsNumber(config.getDouble(fullPath)))
        } else if (configValue.valueType() == ConfigValueType.OBJECT) {
          result += (configName -> JsObject(getNestedMap(fullPath)))
        } else {
          throw new IllegalArgumentException(
            s"Unsupported config type at apm.$configName"
          )
        }
      }

      result
    }

    if (config.hasPath("kibana.user-settings-overrides-json")) {
      val overrides = getNestedMap("kibana.user-settings-overrides-json")

      payload = payload.update(
        Symbol("resources") / Symbol("kibana") / element(0) / Symbol(
          "plan"
        ) / Symbol("kibana") / Symbol("user_settings_override_json") ! set(
          overrides
        )
      )
    }

    logger.info(
      s"preparePayload: ${payload.toString()}"
    )

    payload.toString
  }

  def createDeployment(payload: String): DeploymentInfo = {
    logger.info(s"createDeployment: Creating new deployment")
    val response = httpPost("/deployments?validate_only=false", payload)
    if (response.isDefined)
      logger.info(
        s"createDeployment: Request completed with `${response.get.reason} ${response.get.statusCode}`"
      )
    else throw new RuntimeException(MSG_NO_RESPONSE)
    val res = response.get
    if (!Helper.isValidJson(response.get.jsonString)) {
      throw new RuntimeException(
        "Failed to create new deployment, response is not a JSON"
      )
    }

    val deploymentId = res.jsonString.extract[String](Symbol("id"))
    val username = res.jsonString.extract[String](
      Symbol("resources") / element(0) / Symbol("credentials") / Symbol(
        "username"
      )
    )
    val password = res.jsonString.extract[String](
      Symbol("resources") / element(0) / Symbol("credentials") / Symbol(
        "password"
      )
    )
    val resources = res.jsonString
      .extract[String](Symbol("resources") / elements / Symbol("kind"))
      .toList

    logger.info(
      s"createDeployment: deployment $deploymentId is created"
    )

    DeploymentInfo(deploymentId, username, password, resources)
  }

  def getDeploymentStateInfo(id: String): String = {
    val response = httpGet(
      s"/deployments/$id?enrich_with_template=false&show_metadata=false&show_plans=false"
    )
    if (response.isEmpty) throw new RuntimeException(MSG_NO_RESPONSE)
    response.get.jsonString
  }

  def getStackVersion(id: String): Version = {
    val responseString = getDeploymentStateInfo(id)
    val stackVersion =
      responseString.extract[String](
        Symbol("resources") / Symbol("elasticsearch") / element(0) / Symbol(
          "info"
        ) / Symbol("topology") / Symbol("instances") / element(0) / Symbol(
          "service_version"
        )
      )
    new Version(stackVersion)
  }

  def getInstanceStatus(info: DeploymentInfo): Map[String, String] = {
    val jsonString = getDeploymentStateInfo(info.id)

    info.resources
      .map(item => {
        var status = "undefined"
        try {
          status = jsonString.extract[String](
            Symbol("resources") / item / element(0) / Symbol("info") / Symbol(
              "status"
            )
          )
        } catch {
          case ex: Exception =>
            logger.error(ex.getMessage)
        }
        item -> status
      })
      .toMap
  }

  def getPublicUrls(deploymentId: String): Map[String, String] = {
    val jsonString = getDeploymentStateInfo(deploymentId)
    Map(
      "kibana" -> jsonString.extract[String](
        Symbol("resources") / Symbol("kibana") / element(0) / Symbol(
          "info"
        ) / Symbol("metadata") / Symbol("service_url")
      ),
      "es" -> jsonString.extract[String](
        Symbol("resources") / Symbol("elasticsearch") / element(0) / Symbol(
          "info"
        ) / Symbol("metadata") / Symbol("service_url")
      )
    )
  }

  def waitForClusterToStart(
      info: DeploymentInfo,
      fn: DeploymentInfo => Map[String, String] = getInstanceStatus,
      timeout: Int = DEPLOYMENT_READY_TIMEOUT,
      interval: Int = DEPLOYMENT_POLLING_INTERVAL
  ): Boolean = {
    var started = false
    var timeLeft = timeout
    var poolingInterval = interval
    logger.info(
      s"waitForClusterToStart: waitTime ${timeout}ms, poolingInterval ${poolingInterval}ms"
    )
    while (!started && timeLeft > 0) {
      val statuses = fn(info)
      if (statuses.isEmpty || statuses.values.exists(s => s != "started")) {
        logger.info(
          s"waitForClusterToStart: Deployment is in progress... ${statuses.toString()}"
        )
        timeLeft -= poolingInterval
        sleep(poolingInterval)
      } else {
        logger.info("waitForClusterToStart: Deployment is ready!")
        started = true
      }
    }

    if (!started)
      logger.error(s"Deployment ${info.id} was not ready after $timeout ms")

    started
  }

  def deleteDeployment(id: String): Unit = {
    logger.info(s"deleteDeployment: Deployment $id")
    val response = httpPost(
      s"/deployments/$id/_shutdown?hide=true&skip_snapshot=true"
    )
    if (response.isEmpty) throw new RuntimeException(MSG_NO_RESPONSE)
    logger.info(
      s"deleteDeployment: Finished with status code ${response.get.statusCode}"
    )
  }

  def resetPassword(id: String): Map[String, String] = {
    logger.info(s"Reset password: deployment $id")
    val response = httpPost(
      s"/deployments/$id/elasticsearch/main-elasticsearch/_reset-password"
    )
    if (response.isEmpty) throw new RuntimeException(MSG_NO_RESPONSE)
    response.get.jsonString.parseJson.convertTo[Map[String, String]]
  }

  def getDeployments: Map[String, String] = {
    logger.info(s"Search for running deployments")
    val response =
      httpPost(
        "/deployments/_search",
        Helper.loadJsonString(searchDeploymentsTemplate)
      )
    val jsonString = response.get.jsonString
    val names = jsonString
      .extract[String](
        Symbol("deployments") / elements / Symbol("name")
      )
      .toArray
    val ids = jsonString
      .extract[String](
        Symbol("deployments") / elements / Symbol("id")
      )
      .toArray
    Map() ++ (ids zip names)
  }

  def getVersions(category: String): Array[String] = {
    logger.info(s"Get available version")
    val response = httpGet(
      "/platform/configuration/templates/deployments/global"
    )
    val jsonString = response.get.jsonString
    val seq = jsonString
      .extract[Array[String]](
        filter(
          "template_category_id".is[String](_ == category)
        ) / Symbol("regions") / filter(
          "region_id".is[String](_ == "gcp-us-central1")
        ) / Symbol("versions")
      )
    if (seq.isEmpty) null else seq.head
  }

  def getLatestAvailableVersion(prefix: String, category: String): Version = {
    // get the latest available version on Cloud
    val versions = this
      .getVersions(category)
      .filter(s => s.startsWith(prefix))
      .map(s => new Version(s))
      .sorted
    // get the last version in sorted array
    if (versions.isEmpty) null else versions.last
  }
}
