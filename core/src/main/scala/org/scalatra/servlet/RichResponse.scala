package org.scalatra
package servlet

import http._

import java.io.{OutputStream, PrintWriter}
import javax.servlet.http.{HttpServletResponse, Cookie => ServletCookie}
import scala.collection.JavaConversions._
import scala.collection.mutable.Map

import io.backchat.http.ContentType

case class RichResponse(res: HttpServletResponse) extends Response {
  /**
   * Note: the servlet API doesn't remember the reason.  If a custom
   * reason was set, it will be returned incorrectly here,
   */
  def status: ResponseStatus = ResponseStatus(res.getStatus)

  def status_=(statusLine: ResponseStatus) = 
    res.setStatus(statusLine.code, statusLine.message)

  object headers extends Map[String, String] {
    def get(key: String): Option[String] = 
      res.getHeaders(key) match {
	case xs if xs.isEmpty => None
	case xs => Some(xs mkString ",")
      }

    def iterator: Iterator[(String, String)] = 
      for (name <- res.getHeaderNames.iterator) 
      yield (name, res.getHeaders(name) mkString ", ")

    def +=(kv: (String, String)): this.type = {
      res.setHeader(kv._1, kv._2)
      this
    }

    def -=(key: String): this.type = {
      res.setHeader(key, "")
      this
    }
  }

  def addCookie(cookie: Cookie) {
    res.addCookie(cookie: ServletCookie)
  }

  def characterEncoding: Option[String] =
    Option(res.getCharacterEncoding)

  def characterEncoding_=(encoding: Option[String]) {
    res.setCharacterEncoding(encoding getOrElse null)
  }
  
  def contentType: Option[ContentType] =
    Option(res.getContentType) map toContentType

  def contentType_=(contentType: Option[ContentType]) {
    res.setContentType(contentType map { _.value } getOrElse null) 
  }
  
  def redirect(uri: String) {
    res.sendRedirect(uri)
  }
  
  def outputStream: OutputStream = 
    res.getOutputStream

  def writer: PrintWriter =
    res.getWriter

  def end() = {
    res.flushBuffer()
    res.getOutputStream.close()
  }
}
