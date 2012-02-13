package org.scalatra

import org.jboss.netty.handler.codec.http2.{ HttpMethod => JHttpMethod, HttpResponseStatus }
import org.jboss.netty.handler.codec.http.{CookieDecoder, Cookie => JCookie}


trait NettyImplicits {

  implicit def jHttpMethod2HttpMethod(orig: JHttpMethod): HttpMethod = orig match {
    case JHttpMethod.CONNECT => Connect
    case JHttpMethod.DELETE => Delete
    case JHttpMethod.GET => Get
    case JHttpMethod.HEAD => Head
    case JHttpMethod.OPTIONS => Options
    case JHttpMethod.PATCH => Patch
    case JHttpMethod.POST => Post
    case JHttpMethod.PUT => Put
    case JHttpMethod.TRACE => Trace
  }

  implicit def nettyCookieToRequestCookie(orig: JCookie) =
    RequestCookie(orig.getName, orig.getValue, CookieOptions(orig.getDomain, orig.getPath, orig.getMaxAge, comment = orig.getComment))

  implicit def respStatus2nettyStatus(stat: ResponseStatus) = new HttpResponseStatus(stat.code, stat.message)
  implicit def respStatus2nettyStatus(stat: HttpResponseStatus) = ResponseStatus(stat.getCode, stat.getReasonPhrase)
}

trait Implicits extends NettyImplicits {

  
}