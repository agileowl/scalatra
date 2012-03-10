package org.scalatra
package netty

import org.jboss.netty.channel.{Channels, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.ssl.SslHandler


class ScalatraPipelineFactory(handler: NettyBase) extends ChannelPipelineFactory {

//  private lazy val applicationHandler = new ScalatraApplicationHandler

  def getPipeline = {
    val pipe = Channels.pipeline()

/*    
    applicationContext.server.sslContext foreach { ctxt =>
      pipe.addLast("ssl", new SslHandler(ctxt.createSSLEngine()))
    }
*/
    
    pipe.addLast("decoder", new HttpRequestDecoder)
    pipe.addLast("encoder", new HttpResponseEncoder)

/*    
    applicationContext.server.contentCompression foreach { ctx =>
      pipe.addLast("deflater", new HttpContentCompressor(ctx.level))
    }
*/

    pipe.addLast("requestBuilder", new ScalatraRequestBuilder())
//    pipe.addLast("sessions", applicationHandler)
    pipe.addLast("handler", new ScalatraRequestHandler(handler))
    pipe
  }

  def stop() = {
//    applicationHandler.stop()
  }
}
