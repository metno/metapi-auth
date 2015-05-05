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
  def requestTokenPage = Action {
    Ok(views.html.requestToken(credentialsRequestForm))
  }

  /**
   * Result from having submitted a request for a new api token
   */
  def newClientRequest = Action {
    implicit request =>
      credentialsRequestForm.bindFromRequest.fold(
        formWithErrors => {
          BadRequest(views.html.requestToken(formWithErrors)) //"You need to provide a valid email address to get a key - please try again")
        },
        user => {
          val client = Authorization.newClient(user)
          //Redirect(routes.AuthorizationController.tokenCreated(user, client))
          Redirect(routes.AuthorizationController.tokenCreated).flashing(
            "user" -> user,
            "client" -> client)
        })
  }

  // TODO: Rename to newClientCreated, or something
  //def tokenCreated(user: String, token: String) = Action {
  def tokenCreated() = Action {
    implicit request =>
      Ok(views.html.tokenCreated())
  }

  private val accessTokenRequestForm = Form(
    mapping(
      "grant_type" -> text,
      "client_id" -> text,
      "client_secret" -> text)(AccessTokenRequest.apply)(AccessTokenRequest.unapply))

  def requestAccessToken = Action {
    implicit request =>
      accessTokenRequestForm.bindFromRequest.fold(
        errors => Unauthorized("Invalid input"),
        validRequest => {
          Authorization.generateBearerToken(validRequest) match {
            case Some(key) =>
              Ok(Json.obj("access_token" -> key))
            case _ =>
              Unauthorized("Invalid credentials")
          }
        })
  }
}

// scalastyle:on
