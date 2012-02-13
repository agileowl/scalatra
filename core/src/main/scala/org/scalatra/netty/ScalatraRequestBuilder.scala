package org.scalatra
package netty

import scala.io.Codec
import org.jboss.netty.channel._
import util.MultiMap
import collection.JavaConversions._
import scalaz._
import Scalaz._
import org.jboss.netty.handler.codec.http2.HttpHeaders.Names
import org.jboss.netty.handler.codec.http2.InterfaceHttpData.HttpDataType
import org.jboss.netty.handler.codec.http2.{HttpChunkTrailer, HttpVersion => JHttpVersion, HttpHeaders, FileUpload, Attribute, QueryStringDecoder, HttpChunk, DefaultHttpDataFactory, HttpPostRequestDecoder, HttpRequest => JHttpRequest}
import java.net.{SocketAddress, URI}
import scala.collection.mutable
import java.io.{FileOutputStream, FileInputStream, File}
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBufferFactory, ChannelBuffers, ChannelBufferInputStream}
import scala.util.control.Exception._

class ScalatraRequestBuilder(maxPostBodySize: Long = 2097152)(implicit val appContext: AppContext) extends ScalatraUpstreamHandler {

  @volatile private var request: JHttpRequest = _
  @volatile private var method: HttpMethod = _
  @volatile private var bodyBuffer: Option[File] = None
  
  private val filesToDelete = new mutable.HashSet[File] with mutable.SynchronizedSet[File] 
  
  private val factory = new DefaultHttpDataFactory()
  private var postDecoder: Option[HttpPostRequestDecoder] = None

  private def clearDecoder() = {
    postDecoder foreach (_.cleanFiles())
    postDecoder = None
  } 
  
  private def clearBodyBuffer() {
    (allCatch andFinally {
      filesToDelete.clear()
      bodyBuffer = None
    })(filesToDelete foreach { _.delete() })
  }
  
  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    clearDecoder()
    clearBodyBuffer()
  }
  
  private def isHtmlPost = {
    val ct = request.getHeader(Names.CONTENT_TYPE).blankOption
    method.allowsBody && ct.forall(t => t.startsWith("application/x-www-form-urlencoded") || t.startsWith("multipart/form-data"))
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case request: JHttpRequest => {
        clearDecoder()
        this.request = request
        method = request.getMethod
        bodyBuffer = None
        if (isHtmlPost)
          postDecoder = new HttpPostRequestDecoder(factory, request, Codec.UTF8).some
        
        if (!request.isChunked) sendOn(e.getChannel, e.getRemoteAddress)
        else {
          mangleTransferEncodingHeaders()
          if(!isHtmlPost) initializeChunkedBody(e.getChannel.getConfig.getBufferFactory)
        }
      }
      case chunk: HttpChunk => {
        if (isHtmlPost)
          postDecoder foreach { _ offer chunk }
        else
          addChunkToBody(chunk)
        
        if (chunk.isLast) {
          addTrailingHeaders(chunk)
          if (!isHtmlPost) request.setHeader(Names.CONTENT_LENGTH, contentLength)
          sendOn(e.getChannel, e.getRemoteAddress)
        }
      }
      case _ => ctx sendUpstream e
    }
  }
  
  private def sendOn(channel: Channel, remoteAddress: SocketAddress) {
    val req = scalatraRequest
    request = null
    try { bodyBuffer foreach { _.deleteOnExit() }}
    bodyBuffer = None
    Channels.fireMessageReceived(channel, req, remoteAddress)
  }
  
  private def addTrailingHeaders(chunk: HttpChunk) {
    chunk match {
      case trailer: HttpChunkTrailer => {
        trailer.getHeaders foreach { h =>
          request.setHeader(h.getKey, h.getValue)
        }
      }
      case _ =>
    }
  }
  
  private def contentLength = {
    if (bodyBuffer.isEmpty) {
      request.getContent.readableBytes.toString
    } else {
      ((bodyBuffer map (_.length())) | 0L).toString 
    }
  }
  
  private def mangleTransferEncodingHeaders() {
    val encodings = request.getHeaders(Names.TRANSFER_ENCODING)
    encodings remove HttpHeaders.Values.CHUNKED
    if (encodings.isEmpty) request.removeHeader(Names.TRANSFER_ENCODING)
    else request.setHeader(Names.TRANSFER_ENCODING, encodings)
  }
  
  private def initializeChunkedBody(factory: ChannelBufferFactory) {
    request setChunked false
    request setContent ChannelBuffers.dynamicBuffer(factory)    
  }
  
  private def addChunkToBody(chunk: HttpChunk) {
    if ((request.getContent.readableBytes() + chunk.getContent.readableBytes()) > maxPostBodySize)
      bodyBuffer = overflowToFile(chunk)
    writeToBodyBuffer(chunk.getContent)
  }
  
  private def writeToBodyBuffer(buffer: ChannelBuffer) {
    if (bodyBuffer.isEmpty) request.getContent.writeBytes(buffer)
    else {
      bodyBuffer foreach { pth =>
        new FileOutputStream(pth, true).getChannel.write(buffer.toByteBuffer)
      }
    }
  }

  private def overflowToFile(chunk: HttpChunk) = {
    val tmpFile = File.createTempFile("sclatra-tmp", null, new File(appContext.server.tempDirectory.path.toAbsolute.path))
    new FileOutputStream(tmpFile).getChannel.write(request.getContent.toByteBuffer)
    request.setContent(null)
    tmpFile.deleteOnExit()
    filesToDelete += tmpFile
    tmpFile.some
  }
  
  private def scalatraRequest: HttpRequest = {
    if (isHtmlPost) {
      val (parameters, files) = (postDecoder map readPostData) | (MultiMap(), Seq.empty[HttpFile])
      new NettyHttpRequest(
        method,
        URI.create(request.getUri),
        headers,
        queryString,
        parameters,
        files,
        serverProtocol,
        new ChannelBufferInputStream(ChannelBuffers.buffer(0)))
    } else {
      new NettyHttpRequest(
        method,
        URI.create(request.getUri),
        headers,
        queryString,
        MultiMap(),
        Seq.empty,
        serverProtocol,
        inputStream)
    }
  }
  
  private def queryString = new QueryStringDecoder(request.getUri).getParameters.mapValues(_.toSeq): MultiMap
  
  private def headers = Map((request.getHeaders map { e => e.getKey -> e.getValue.blankOption.orNull }):_*)
  
  private def inputStream = {
    if (bodyBuffer.isEmpty)
      new ChannelBufferInputStream(request.getContent)
    else
      new FileInputStream(bodyBuffer.get)
  }
  
  private def serverProtocol = request.getProtocolVersion match {
    case JHttpVersion.HTTP_1_0 => Http10
    case JHttpVersion.HTTP_1_1 => Http11
  }
  
  private def defaultMultiMap = MultiMap().withDefaultValue(Seq.empty)
  private def readPostData(decoder: HttpPostRequestDecoder): (Map[String, Seq[String]], Seq[HttpFile]) = {
    decoder.getBodyHttpDatas.foldLeft((defaultMultiMap, Seq.empty[HttpFile])) { (acc, data) =>
      val (container, files) = acc
      data match {
        case d: Attribute if d.getHttpDataType == HttpDataType.Attribute => {
          (container + (d.getName -> (Seq(d.getValue) ++ container(d.getName))), files)
        }
        case d: FileUpload if d.getHttpDataType == HttpDataType.FileUpload => {
          logger debug "Receiving file: %s".format(d.getName)
          (container, files :+ new NettyHttpFile(d))
        }
        case _ => acc
      }
    }
  }

}