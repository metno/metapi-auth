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

package controllers.authorization

import play.api._
import play.api.Play.current
import play.api.mvc._
import util._
import no.met.security._
import play.api.data._
import play.api.data.Forms._
import controllers._
import no.met.security.AuthorizedAction
import play.api.libs.json._

// scalastyle:off public.methods.have.type

object AuthorizationController extends Controller {

  /**
   * Sample page to verify that authorization mechanism works
   */
  def secret = AuthorizedAction {
    Ok("Don't tell\n")
  }

  private val credentialsRequestForm = Form(single("email" -> email))

  /**
   * Get the page to order api tokens
   *
   * TODO: Rename to requestNewClientPage or something
   */
  def requestCredentialsPage = Action {
    Ok(views.html.requestCredentials(credentialsRequestForm))
  }

  /**
   * Result from having submitted a request for a new api token
   */
  def newCredentialsRequest = Action {
    implicit request =>
      credentialsRequestForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.requestCredentials(formWithErrors)) //"You need to provide a valid email address to get a key - please try again")
        },
        user => {

          /**
           * Returns a map that contains scheme, server, and pathPrefix derived from the service.* values in the configuration as follows:
           *
           *   - scheme: service.scheme, defaulting to https in PROD mode and to http in DEV/TEST mode.
           *   - server: service.host:service.port if service.port is a valid port number, otherwise service.host.
           *             If service.host is missing in PROD mode, an exception is thrown. In DEV/TEST mode, service.host defaults to localhost.
           *   - pathPrefix: service.pathPrefix, defaulting to "".
           */
          def getServiceConf: Map[String, String] = {

            var scheme = ""
            var host = ""

            if (play.api.Play.isProd(play.api.Play.current)) { // PROD mode
              scheme = current.configuration.getString("service.scheme") getOrElse "https"
              host = current.configuration.getString("service.host").get // fail and propagate internal server error (500) if absent
            } else { // DEV or TEST mode
              scheme = current.configuration.getString("service.scheme") getOrElse "http"
              host = current.configuration.getString("service.host") getOrElse "localhost"
            }

            def validPort(s: String): Boolean = { // returns true iff s can be converted to a valid port number
              scala.util.Try(s.toInt) match {
                case scala.util.Success(x) => (x >= 0) && (x <= 65535)
                case _                     => false
              }
            }

            Map(
              "scheme" -> scheme,
              "server" -> {
                (current.configuration.getString("service.port") getOrElse "") match {
                  case port if (validPort(port)) => s"$host:$port"
                  case _                         => host
                }
              },
              "pathPrefix" -> (current.configuration.getString("service.pathPrefix") getOrElse ""))
          }

          val client = Authorization.newClient(user)
          Logger.debug(s"Registered key ${client} for user ${user}")
          val serviceConf = getServiceConf
          Redirect(routes.AuthorizationController.credentialsCreated).flashing(
            "scheme" -> serviceConf("scheme"),
            "server" -> serviceConf("server"),
            "pathPrefix" -> serviceConf("pathPrefix"),
            "user" -> user,
            "id" -> client.id,
            "secret" -> client.secret)
        })
  }

  // TODO: Rename to newClientCreated, or something
  def credentialsCreated() = Action {
    implicit request =>
      Ok(views.html.credentialsCreated())
  }

  private val accessTokenRequestForm = Form(
    mapping(
      "grant_type" -> text,
      "client_id" -> text,
      "client_secret" -> text)(AccessTokenRequest.apply)(AccessTokenRequest.unapply))

  def requestAccessToken = Action {
    implicit request =>
      accessTokenRequestForm.bindFromRequest.fold(
        errors => BadRequest("Invalid input\n"),
        validRequest => {
          Authorization.generateBearerToken(validRequest) match {
            case Success(key) =>
              Ok(Json.obj("access_token" -> key))
            case Failure(x) => {
              Logger.debug(x.getMessage)
              Unauthorized("Invalid credentials\n")
            }
          }
        })
  }
}

// scalastyle:on
