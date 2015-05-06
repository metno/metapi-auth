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
import java.util.UUID
import java.sql.SQLException
import scala.util._
import com.github.nscala_time.time.Imports._

/**
 * Functionality for creating and verifying unique keys for identifying users.
 */
object Authorization {

  private def createUniqueKey() = UUID.randomUUID
  private implicit def uuidToStatement = new ToStatement[UUID] {
    def set(s: java.sql.PreparedStatement, index: Int, aValue: UUID): Unit = s.setObject(index, aValue)
  }

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

      val previouslyExisting = SQL("SELECT client_id FROM authorized_keys WHERE owner={email}")
        .on("email" -> userEmail)
        .apply()
      if (previouslyExisting.isEmpty) {
        val key = createUniqueKey()
        SQL("INSERT INTO authorized_keys (client_id, owner) VALUES ({client_id}::uuid, {email})")
          .on("client_id" -> key, "email" -> userEmail)
          .executeInsert()
        key toString
      } else {
        val row = previouslyExisting.head
        row[UUID]("client_id") toString
      }
    }
  }

  /**
   * Get the id associated with the given api key
   */
  private def idOf(clientId: UUID): Long =
    DB.withConnection("authorization") { implicit conn =>
      val result = SQL("SELECT owner_id FROM authorized_keys WHERE client_id={clientId} AND active='true'")
        .on("clientId" -> clientId)
        .apply() map {
          row =>
            row[Long]("owner_id")
        }

      if (result.isEmpty) {
        throw new Exception("No user valid associated with the given key: " + clientId)
      }

      result head
    }

  /**
   * Check if the given access token request is valid
   *
   * @param request The request for a token
   * @return false, unless the given request is valid and should be permitted
   */
  def authorized(request: AccessTokenRequest): Try[Long] = Try {
    if (request == null || // scalastyle:ignore null
      request.grantType != "client_credentials" ||
      !request.clientSecret.isEmpty()) {
      throw new Exception("Missing required authorization fields in request")
    }
    idOf(UUID.fromString(request.clientId))
  }

  /**
   * Generate a time-limited bearer token, that can be used to access protected services.
   *
   * @param request The request for a token
   * @param timeToLive How long the returned token should be valid
   * @return a valid token, or None if authorized(request) is false
   */
  def generateBearerToken(request: AccessTokenRequest, timeToLive: Duration = 10 minutes): Try[String] = {

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
      case scala.util.Success(bearerToken) =>
        bearerToken.isValid
      case Failure(x) => {
        Logger.debug(x.getMessage)
        false
      }
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
