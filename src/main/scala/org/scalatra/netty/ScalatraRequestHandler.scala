package org.scalatra
package netty

import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http2.{HttpVersion => JHttpVersion, HttpResponseStatus, DefaultHttpResponse}

/**
 * This handler is akin to the handle method of scalatra
 */
class ScalatraRequestHandler(implicit val appContext: AppContext) extends ScalatraUpstreamHandler {

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage match {
      case req: NettyHttpRequest => {
        logger debug ("Received request to: %s" format req.uri.toASCIIString)
        val resp = req newResponse ctx
        val app = appContext.application(req)
        if (app.isDefined) {
          app.get(req, resp)
        } else {
          logger warn  ("Couldn't match the request: %s" format req.uri.toASCIIString)
          val resp = new DefaultHttpResponse(JHttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
          resp.setContent(ChannelBuffers.wrappedBuffer("Not Found".getBytes("UTF-8")))
          ctx.getChannel.write(resp).addListener(ChannelFutureListener.CLOSE)
        }
      }
    }
  }

}