/*
    MET-API

    Copyright (C) 2016 met.no
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

import play.api.Logger
import com.github.nscala_time.time.Imports._
import scala.language.postfixOps
import scala.util._

class MetApiUser(val identifier: Long, val permissions: Set[Int]) {

  def authorized(permissionsRequired: Traversable[Int]): Boolean = {
    !permissionsRequired.exists(!authorized(_))
  }

  def authorized(permissionRequired: Int): Boolean = {
    permissions contains permissionRequired
  }

}

/**
 * A request for a client access token
 */
case class AccessTokenRequest(grantType: String, clientId: String, clientSecret: String) {

  def credentials: ClientCredentials = ClientCredentials(clientId, clientSecret)

  /**
   * Check if the given access token request is valid
   *
   * @param request The request for a token
   * @return id of the authorized user
   */
  def userData: Try[MetApiUser] = {
    if (grantType != "client_credentials") {
      Failure(new Exception("Missing required authorization fields in request"))
    }
    else {
      credentials.user
    }
  }

  /**
   * Generate a time-limited bearer token, that can be used to access protected services.
   *
   * @param request The request for a token
   * @param timeToLive How long the returned token should be valid
   * @return a valid token, or None if not authorized
   */
  def generateBearerToken(timeToLive: Duration = 10 minutes): Try[String] = {
    Logger.debug (grantType + "," + clientId + "," + clientSecret)
    userData map { user =>
      BearerToken.create(user.identifier, timeToLive, user.permissions).encoded
    }
  }

}
