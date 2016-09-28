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
package controllers

import javax.inject.Inject
import play.api.Play.current
import play.api._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc._
import scala.util._
import no.met.data._
import no.met.security._
import no.met.security.AuthorizedAction
import views._

// scalastyle:off public.methods.have.type

class AuthorizationController @Inject() (val messagesApi: MessagesApi) extends Controller with I18nSupport {

  /**
   * Sample page to verify that authorization mechanism works
   */
  def secret = AuthorizedAction {
    Ok("Don't tell\n")
  }

  /**
   * Sample page to test user permissions
   */
  def topSecret = PermissionRestrictedAction(Seq(0)) { request =>

    val r = request.asInstanceOf[AuthorizededRequest[AnyContent]]
    val user = r.user

    val permissions = user.permissions.mkString
    var text = s"This is _really_ secret!! "
    if (user.authorized(1)) {
      text += "You are privileged!\n"
    }

    Ok(text)
  }

  def logout = Action {
    implicit request =>
      throw new UnauthorizedException("Credentials have been invalidated.")
  }

  // Define form
  val clientIdRequestForm = Form(single("email" -> email))

  // Create request page with form
  def requestClientIdPage = Action {
    Ok(views.html.requestCredentials(clientIdRequestForm))
  }

  // Handle result of a form submitted via the form
  def newClientIdRequest = Action {
    implicit request =>
      clientIdRequestForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.requestCredentials(formWithErrors)) //"You need to provide a valid email address to get a key - please try again")
        },
        user => {
          val client = ClientCredentials.create(user)
          //Logger.debug(s"Registered key ${client} for user ${user}")
          val serviceConf = ConfigUtil.serviceConf
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
        errors => throw new BadRequestException("Invalid input."),
        validRequest => {
          validRequest.generateBearerToken() match {
            case Success(key) =>
              Ok(Json.obj("access_token" -> key))
            case Failure(x) => {
              throw new UnauthorizedException("Invalid credentials.")
            }
          }
        })
  }
  
}

// scalastyle:on
