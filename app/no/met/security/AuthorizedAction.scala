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

import play.api._
import play.api.mvc._
import play.api.Mode
import play.api.Play.current
import scala.concurrent.Future
import scala.util._
import no.met.security._

/**
 * Providing access only to users who provide a valid Authorization field in the http
 * header when performing requests.
 *
 * Note that this functionality may be turned off in application.conf, by
 * setting auth.active to false.
 */
object AuthorizedAction extends PermissionRestrictedAction(Seq.empty[Int])

class AuthorizededRequest[A](val user: MetApiUser, request: Request[A]) extends WrappedRequest[A](request)

case class PermissionRestrictedAction(permissionsRequired: Traversable[Int])
    extends ActionBuilder[Request] with ActionTransformer[Request, AuthorizededRequest] {

  private def authenticate[A](request: Request[A]): MetApiUser = {
    request.headers.get("Authorization") match {
      case Some(x) => Authentication.identifyUser(x)
      case None => throw new UnauthorizedException("Missing authentication token")
    }
  }

  private def shouldSkipAuthorization: Boolean = {
    current.configuration.getBoolean("auth.active") == Some(false)
  }

  override protected def transform[A](request: Request[A]): Future[AuthorizededRequest[A]] = Future.successful {
    if (shouldSkipAuthorization) {
      new AuthorizededRequest(new MetApiUser(-1, Set.empty[Int]), request)
    } else {
      val user = authenticate(request)
      if (!user.authorized(permissionsRequired)) {
        throw new UnauthorizedException("Not authorized to access the requested data")
      }
      new AuthorizededRequest(user, request)
    }
  }
}
