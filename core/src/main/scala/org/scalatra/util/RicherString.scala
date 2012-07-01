package org.scalatra
package util

import java.util.regex.Pattern
import rl.UrlCodingUtils

class RicherString(orig: String) {
  def isBlank = orig == null || orig.trim.isEmpty

  @deprecated("Use nonBlank instead", "2.0")
  def isNonBlank = !isBlank

  @deprecated("Use blankOption instead", "2.0")
  def toOption = blankOption

  def blankOption = if (isBlank) None else Some(orig)

  def nonBlank = !isBlank

  def urlEncode = UrlCodingUtils.urlEncode(orig)

  def formEncode = UrlCodingUtils.urlEncode(orig, spaceIsPlus = true)

  def urlDecode = UrlCodingUtils.urlDecode(orig)

  def formDecode = UrlCodingUtils.urlDecode(orig, plusIsSpace = true)

  def /(path: String) = (orig.endsWith("/"), path.startsWith("/")) match {
    case (true, false) | (false, true) ⇒ orig + path
    case (false, false) ⇒ orig + "/" + path
    case (true, true) ⇒ orig + path.substring(1)
  }

  def regexEscape = Pattern.quote(orig)
}

object RicherString {
  implicit def stringToRicherString(s: String) = new RicherString(s)
}
