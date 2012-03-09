package org.scalatra

import scala.collection.Map
import io.backchat.http.ContentType

trait HttpMessage {
  /**
   * A map of headers.  Multiple header values are separated by a ','
   * character.  The keys of this map are case-insensitive.
   */
  def headers: Map[String, String]

  /**
   * The content of the Content-Type header, or None if absent.
   */
  def contentType: Option[ContentType]

  /**
   * Returns the name of the character encoding of the body, or None if no
   * character encoding is specified.
   */
  def characterEncoding: Option[String]
}
