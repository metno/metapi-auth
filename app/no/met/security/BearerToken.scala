/*
    MET-API

    Copyright (C) 2014 met.no
    Contact information:
    Norwegian Meteorological Institute
    Box 43 Blindern
    0313 OSLO
    NORWAY
    E-mail: met-api@met.no

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
    MA 02110-1301, USA
*/

package no.met.security

import play.api.Play.current
import play.api.Logger
import com.github.nscala_time.time.Imports._
import scala.util._
import java.security._
import java.nio.ByteBuffer
import javax.crypto._
import javax.crypto.spec._
import javax.xml.bind.DatatypeConverter
import com.google.common.primitives._
import com.google.common.io._

/**
 * A time-limited bearer token, providing time-limited access to protected
 * products to a user
 */
case class BearerToken(userId: Long, expires: DateTime) {

  /**
   * Actual data
   */
  private lazy val payload = {

    val buffer = ByteBuffer.allocate(Longs.BYTES * 2)
    buffer.putLong(userId)
    buffer.putLong(Longs.BYTES, expires.toDate().getTime)
    BearerToken.encoding.encode(buffer.array())
  }

  /**
   * Identifier, showing that this data was created by us
   */
  private lazy val signature = BearerToken.sign(payload)

  /**
   * Can the token (still) be used to access data?
   */
  def isValid: Boolean = {
    expires > DateTime.now
  }

  /**
   * Get the string representation of this token
   */
  def encoded: String = {
    s"$signature|$payload"
  }

  /**
   * String summary of object, for logging or debugging
   */
  override def toString: String = {
    s"BearerToken($userId, ${expires.toString})"
  }
}

object BearerToken {

  private val encoding = BaseEncoding.base64Url()

  /**
   * Create a new token, that expires after the given time
   */
  def create(userId: Long, timeToLive: Duration): BearerToken = {
    BearerToken(userId, DateTime.now + timeToLive)
  }

  /**
   * Parse a token string, as encoded by bearerToken.encoded. Will return None
   * if unable to parse, or signature is invalid
   */
  def parse(encoded: String): Option[BearerToken] = {
    val tokens = encoded.split("\\|")
    if (tokens.length == 2) {
      val signature = tokens(0)
      val payload = tokens(1)
      if (sign(payload) == signature) {
        parsePayload(payload)
      } else {
        Logger.debug("Invalid signature for bearer token " + encoded)
        None
      }
    } else {
      Logger.debug("Unable to separate checksum from payload in bearer token: " + encoded)
      None
    }
  }

  private def parsePayload(payload: String): Option[BearerToken] = Try {

    val data = encoding.decode(payload)
    val userId = ByteBuffer.wrap(data, 0, Longs.BYTES).getLong()
    val expirationTime = ByteBuffer.wrap(data, Longs.BYTES, Longs.BYTES).getLong()
    BearerToken(userId, new DateTime(expirationTime))
  } match {
    case Success(token) if (token isValid) => Some(token)
    case _ => {
      Logger.debug("Unable to parse bearer token payload: " + payload)
      None
    }
  }

  private val encryptionKey: Array[Byte] = {

    // TODO: Revise this

    val key = current.configuration.getString("application.secret").get
    val keyLength = 32
    key.substring(0, keyLength).getBytes
  }

  private val messageDigest = MessageDigest.getInstance("MD5")

  private val cipher: Cipher = {
    val encryptionMethod = "AES"
    val cipher = Cipher.getInstance(encryptionMethod)
    val key = new SecretKeySpec(encryptionKey, encryptionMethod)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    cipher
  }

  private def sign(payload: String): String = {
    val digest = messageDigest.digest(payload.getBytes)
    val encrypted = cipher.doFinal(digest)
    encoding.encode(encrypted)
  }
}
