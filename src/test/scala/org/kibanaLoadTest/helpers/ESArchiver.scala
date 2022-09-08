package org.kibanaLoadTest.helpers

import io.circe.Json
import io.circe.parser._
import io.circe.syntax.EncoderOps
import org.kibanaLoadTest.KibanaConfiguration
import org.kibanaLoadTest.helpers.Helper.checkFilesExist
import org.slf4j.{Logger, LoggerFactory}
import java.nio.file.{Path, Paths}

case class Doc(indexType: String, name: String, source: Json)

class ESArchiver(config: KibanaConfiguration, bulkSize: Int = 1000) {
  val logger: Logger = LoggerFactory.getLogger("ESArchiver")
  private val MAPPINGS_FILENAME = "mappings.json"
  private val DATA_FILENAME = "data.json.gz"
  private val INDEX_EXISTS_ERROR =
    "[es/indices.create] failed: [resource_already_exists_exception]"

  def unload(archivePath: Path): Unit = {
    val client = ESClient.getInstance(
      Helper.parseUrl(config.esUrl),
      config.username,
      config.password
    )
    try {
      val mappingsPath = Paths.get(archivePath.toString, MAPPINGS_FILENAME)
      checkFilesExist(mappingsPath)
      val indexArray = this.readDataFromFile(mappingsPath)

      for (index <- indexArray) {
        logger.info(s"[$archivePath] Unloading '${index.name}' index")
        client.deleteIndex(index.name)
      }
    } catch {
      case e: Throwable =>
        logger.error(s"Exception occurred ${e.printStackTrace()}")
    } finally {
      client.closeConnection()
    }
  }

  def load(archivePath: Path): Unit = {
    val client = ESClient.getInstance(
      Helper.parseUrl(config.esUrl),
      config.username,
      config.password
    )
    try {
      val mappingsPath = Paths.get(archivePath.toString, MAPPINGS_FILENAME)
      val dataPath = Paths.get(archivePath.toString, DATA_FILENAME)
      checkFilesExist(mappingsPath, dataPath)
      logger.info(s"[$archivePath] Loading '$MAPPINGS_FILENAME'")
      val indexArray = this.readDataFromFile(mappingsPath)
      logger.info(s"[$archivePath] Loading '$DATA_FILENAME'")
      val docsArray = this.readDataFromFile(dataPath)

      indexArray.length match {
        case 0 => throw new RuntimeException("No index found in mappings.json")
        case 1 =>
          val index = indexArray.last
          logger.info(s"[$archivePath] Creating ${index.name} index")
          client.createIndex(index.name, index.source)
          logger.info(s"[$archivePath] Indexing docs")
          client.bulk(
            index.name,
            docsArray.map(doc => doc.source),
            bulkSize
          )
        case _ =>
          throw new RuntimeException("More than 1 index found in mappings.json")
      }
    } catch {
      case e: RuntimeException =>
        e.getMessage match {
          case "createIndex" =>
            e.getCause.getMessage.startsWith(INDEX_EXISTS_ERROR) match {
              case true  => logger.warn(e.getCause.getMessage)
              case false => throw new RuntimeException(e)
            }
          case "bulk" => throw new RuntimeException(e)
        }
    } finally {
      client.closeConnection()
    }
  }

  def convertToDoc(json: Json): Doc = {
    val _type = json.hcursor.get[String]("type").getOrElse(null)
    val index =
      json.hcursor.downField("value").get[String]("index").getOrElse(null)
    val value = json.hcursor.get[Json]("value").getOrElse(null)
    val source = if (_type == "doc") {
      json.hcursor.downField("value").get[Json]("source").getOrElse(null)
    } else if (_type == "index") {
      value.withObject(jsonObj => jsonObj.remove("index").asJson)
    } else {
      throw new RuntimeException(s"Unknown ${_type} type")
    }
    Doc(_type, index, source)
  }

  def readDataFromFile(
      filePath: Path
  ): Array[Doc] = {
    Helper
      .readArchiveFile(filePath)
      .map(jsonString => parse(jsonString).getOrElse(Json.Null))
      .map(json => convertToDoc(json))
      .toArray[Doc]
  }
}
