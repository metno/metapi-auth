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
  def newClient(userEmail: String): ClientCredentials = {
    DB.withTransaction("authorization") { implicit conn =>

      val id = createUniqueKey()
      val secret = createUniqueKey()
      SQL("INSERT INTO authorized_keys (client_id, client_secret, email) VALUES ({client_id}, {client_secret}, {email})")
        .on("client_id" -> id, "client_secret" -> secret, "email" -> userEmail)
        .executeInsert()
      ClientCredentials(id toString, secret toString)
    }
  }

  @throws[Exception]("If unable to access database, or not authenticated")
  private def authenticate(client: ClientCredentials): Long = {
    DB.withConnection("authorization") { implicit conn =>
      val result = SQL("SELECT owner_id FROM authorized_keys WHERE client_id={id}::uuid AND client_secret={secret}::uuid AND active='true'")
        .on("id" -> client.id, "secret" -> client.secret)
        .apply() map {
          row =>
            row[Long]("owner_id")
        }
      result.size match {
        case 0 => throw new NoSuchElementException(s"Unsuccessful authentication attempt with client_id ${client.id}")
        case 1 => result(0)
        case _ => throw new NoSuchElementException(s"Several existing clients share the same id: ${client.id}")
      }
    }
  }

  /**
   * Check if the given access token request is valid
   *
   * @param request The request for a token
   * @return id of the authorized user
   */
  def authorized(request: AccessTokenRequest): Try[Long] = Try {
    if (request == null || // scalastyle:ignore null
      request.grantType != "client_credentials") {
      throw new Exception("Missing required authorization fields in request")
    }
    // At this time authenticated == authorized
    authenticate(request.credentials)
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

  @throws[Exception]("If unable to access database, or not authenticated")
  private def identify(clientId: String): Try[Long] = Try {
    DB.withConnection("authorization") { implicit conn =>
      val result = SQL("SELECT owner_id FROM authorized_keys WHERE client_id={id}::uuid AND active='true'")
        .on("id" -> clientId)
        .apply() map {
          row =>
            row[Long]("owner_id")
        }
      result.size match {
        case 0 => throw new NoSuchElementException(s"Unsuccessful authentication attempt with client_id ${clientId}")
        case 1 => result(0)
        case _ => throw new NoSuchElementException(s"Several existing clients share the same id: ${clientId}")
      }
    }
  }

  /**
   * Check basic authorization
   */
  def validateBasicAuth(token: String): Boolean = {
    val id = BasicAuth.parse(token)
    identify(BasicAuth.parse(token)) match {
      case scala.util.Success(id) => true
      case Failure(x) => false
    }
  }

  /**
   * Check if the given Authorization string is valid.
   *
   * @param authorization string, as it should look as parameter to http header Authorization
   */
  def validateAuthorization(credentials: String): Boolean = {

    if (credentials.startsWith("Bearer ")) {
      val tokenIdentifierLength = 7 // "Bearer ".size
      validateBearerToken(credentials.substring(tokenIdentifierLength).trim())
    } else if (credentials.startsWith("Basic ")) {
      val basicIdentifierLength = 6 // "Basic ".size
      validateBasicAuth(credentials.substring(basicIdentifierLength).trim())
    } else {
      Logger.debug("Rejecting provided authorization token: " + credentials)
      false
    }
  }
}
