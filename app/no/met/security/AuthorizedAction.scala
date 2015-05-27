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
import no.met.security._
import scala.util._

/**
 * Providing access only to users who provide a valid Authorization field in the http
 * header when performing requests.
 *
 * Note that this functionality may be turned off in application.conf, by
 * setting auth.active to false.
 */
object AuthorizedAction extends ActionBuilder[Request] with ActionFilter[Request] {

  private def authorize[A](request: Request[A]) = {
    request.headers.get("Authorization") match {
      case Some(x) if (Authorization.validateAuthorization(x)) => None
      case Some(_) => Some(Results.Unauthorized("Unrecognized authentication token\n").withHeaders(("WWW-Authenticate","Basic realm=\"METAPI\"")))
      case None => Some(Results.Unauthorized("Missing authentication token\n").withHeaders(("WWW-Authenticate","Basic realm=\"METAPI\"")))
    }
  }

  private def shouldSkipAuthorization(): Boolean = {
    Seq(Mode.Dev, Mode.Test).contains(current.mode) && current.configuration.getBoolean("auth.active") == Some(false)
  }

  override def filter[A](request: Request[A]) = Future.successful { // scalastyle:ignore public.methods.have.type
    Try {
      shouldSkipAuthorization()
    } match {
      case Success(false) => authorize(request)
      case Success(true) => None
      case Failure(x) => Some(Results.InternalServerError("Internal error"))
    }
  }
}
