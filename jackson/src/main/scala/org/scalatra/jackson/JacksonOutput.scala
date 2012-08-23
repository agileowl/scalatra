package org.scalatra
package jackson

import com.fasterxml.jackson.databind._
import java.io.Writer
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import xml.XML
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.ScalaModule

private[jackson] trait JacksonOutput extends json.JsonOutput {

  protected def useBigDecimalForFloats: Boolean = false
  protected def useBigIntForInts: Boolean = false

  protected val jsonMapper = new ObjectMapper()
  val xmlMapper = new XmlMapper()
  configureJackson(jsonMapper)
  configureJackson(xmlMapper)

  protected def configureJackson(mapper: ObjectMapper) {
    val scalaModule = new DefaultScalaModule
    
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, useBigDecimalForFloats)
    mapper.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, useBigIntForInts)
    mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
  }

  protected type JsonType = JsonNode

  protected def jsonClass: Class[_] = classOf[JsonType]

  protected def writeJsonAsXml(json: JsonType, writer: Writer) {
    val nodes = xmlRootNode.copy(child = XML.loadString(xmlMapper.writeValueAsString(json)).child)
    XML.write(response.writer, xml.Utility.trim(nodes), response.characterEncoding.get, xmlDecl = true, null)
  }

  protected def writeJson(json: JsonType, writer: Writer) {
    jsonMapper.writeValue(writer, json)
  }
}
