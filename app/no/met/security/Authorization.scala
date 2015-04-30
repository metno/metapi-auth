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
import play.api._
import anorm._
import play.api.db._
import java.sql.SQLException
import scala.util._
import com.github.nscala_time.time.Imports._

/**
 * Functionality for creating and verifying unique keys for identifying users.
 */
object Authorization {

  /**
   * Create a unique key to use as client_id
   */
  private def createUniqueKey(): String = java.util.UUID.randomUUID.toString

  /**
   * Create new client credentials, associated with the given email address.
   * The credentials may be used when calling generateBearerToken and
   * validateToken.
   *
   * @param userEmail The email address the generated key should be associated with
   * @return Valid credentials, associated with the given email address
   */
  def newClient(userEmail: String): String = {
    DB.withTransaction("authorization") { implicit conn =>

      val previouslyExisting = SQL("SELECT api_key FROM authorized_keys WHERE owner={email}")
        .on("email" -> userEmail)
        .apply()
      if (previouslyExisting.isEmpty) {
        val key = createUniqueKey()
        SQL("INSERT INTO authorized_keys (api_key, owner) VALUES ({api_key}, {email})")
          .on("api_key" -> key, "email" -> userEmail)
          .executeInsert()
        key
      } else {
        val row = previouslyExisting.head
        row[String]("api_key")
      }
    }
  }

  /**
   * Get the id associated with the given api key
   */
  private def idOf(apiKey: String): Option[Long] =
    DB.withConnection("authorization") { implicit conn =>
      val result = SQL("SELECT owner_id FROM authorized_keys WHERE api_key={token} AND active='true'")
        .on("token" -> apiKey)
        .apply() map {
          row =>
            row[Option[Long]]("owner_id")
        }

      if (result.isEmpty) {
        Logger.debug("No user valid associated with the given key: " + apiKey)
        None
      } else {
        result head
      }
    }

  /**
   * Check if the given access token request is valid
   *
   * @param request The request for a token
   * @return false, unless the given request is valid and should be permitted
   */
  def authorized(request: AccessTokenRequest): Option[Long] = {
    if (request != null && // scalastyle:ignore null
      request.grantType == "client_credentials" &&
      request.clientSecret.isEmpty()) {
      idOf(request.clientId)
    } else {
      Logger.debug("Missing required authorization fields in request")
      None
    }
  }

  /**
   * Generate a time-limited bearer token, that can be used to access protected services.
   *
   * @param request The request for a token
   * @param timeToLive How long the returned token should be valid
   * @return a valid token, or None if authorized(request) is false
   */
  def generateBearerToken(request: AccessTokenRequest, timeToLive: Duration = 10 minutes): Option[String] = {

    authorized(request) map { userId: Long =>
      val token = BearerToken.create(userId, timeToLive)
      token.encoded
    }
  }

  /**
   * Check if the given bearer token is (still) valid
   */
  def validateBearerToken(token: String): Boolean = {
    BearerToken.parse(token) match {
      case Some(bearerToken) => {
        if (bearerToken.isValid) {
          Logger.debug(s"Successful access by userid=${bearerToken.userId}")
          true
        } else {
          false
        }
      }
      case _ => false
    }
  }

  /**
   * Check if the given Authorization string is valid.
   *
   * @param authorization string, as it should look as parameter to http header Authorization
   */
  def validateAuthorization(token: String): Boolean = {

    if (token.startsWith("Bearer ")) {
      val tokenIdentifierLength = 7 // "Bearer ".size
      validateBearerToken(token.substring(tokenIdentifierLength).trim())
    } else {
      Logger.debug("Rejecting provided authorization token: " + token)
      false
    }
  }
}
