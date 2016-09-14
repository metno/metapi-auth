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

import play.api.Play.current
import play.api._
import java.sql.SQLException
import scala.util._
import com.github.nscala_time.time.Imports._

// TODO: Rename object to something more correct

/**
 * Functionality for creating and verifying unique keys for identifying users.
 */
object Authentication {

  /**
   * Check if the given bearer token is (still) valid
   */
  private def validateBearerToken(token: String): MetApiUser = {
    BearerToken.parse(token) match {
      case scala.util.Success(bearerToken) if (bearerToken.isValid) => bearerToken.user
      case _ => throw new UnauthorizedException("Invalid bearer token")
    }
  }

  /**
   * Check basic authorization
   */
  private def validateBasicAuth(token: String): MetApiUser = {
    ClientCredentials.simpleIdentify(BasicAuth.parse(token))
  }

  /**
   * Find out for sure who the user is. Will throw UnauthorizedException on authentication failure
   *
   * @param authorization string, as it should look as parameter to http header Authorization
   */
  def identifyUser(credentials: String): MetApiUser = {

    val idx = credentials.indexOf(" ")
    if (idx < 0 || idx >= credentials.size) {
      throw new UnauthorizedException("Unrecognized authentication token")
    }
    val authorizationMethod = credentials.substring(0, idx)
    val authorizationKey = credentials.substring(idx + 1).trim()

    authorizationMethod match {
      case "Bearer" => validateBearerToken(authorizationKey)
      case "Basic" => validateBasicAuth(authorizationKey)
      case _ => throw new UnauthorizedException("Unrecognized authentication method: " + authorizationMethod)
    }

  }
}
