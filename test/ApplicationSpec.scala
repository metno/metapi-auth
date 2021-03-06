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

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import no.met.security._
import no.met.data._
import play.api.http.HeaderNames
import play.api.mvc.AnyContentAsFormUrlEncoded
import net.sf.ehcache.config.Configuration
import play.mvc.Results.Redirect
import anorm._
import play.api.db._
import play.api.Play.current

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  def getAccessToken(user: String = "someone@met.no"): String = {
    val client = Authorization.newClient(user)
    val body = AnyContentAsFormUrlEncoded(
      Map("grant_type" -> List("client_credentials"),
        "client_id" -> List(client.id),
        "client_secret" -> List(client.secret)))

    val result = route(FakeRequest(POST, "/requestAccessToken").withBody(body)).get
    status(result) must equalTo(OK)
    val contents = contentAsJson(result)
    val accessToken = contents \ "access_token"
    accessToken.as[String]
  }

  def getSecretToken(): String = {
    val body = AnyContentAsFormUrlEncoded(
      Map("grant_type" -> List("client_credentials"),
        "client_id" -> List("cafebabe-feed-d00d-badd-5eaf00d5a1ad"),
        "client_secret" -> List("f007ba11-dad5-f1ed-dead-decafc0ffee5")))

    val result = route(FakeRequest(POST, "/requestAccessToken").withBody(body)).get
    status(result) must equalTo(OK)
    val contents = contentAsJson(result)
    val accessToken = contents \ "access_token"
    accessToken.as[String]
  }

  def getTopSecretToken(): String = {
    val body = AnyContentAsFormUrlEncoded(
      Map("grant_type" -> List("client_credentials"),
        "client_id" -> List("cafebabe-feed-d00d-cafe-5eaf00d5a1ad"),
        "client_secret" -> List("f007ba11-dad5-f1ed-dead-decafc0ffee5")))

    val result = route(FakeRequest(POST, "/requestAccessToken").withBody(body)).get
    status(result) must equalTo(OK)
    val contents = contentAsJson(result)
    val accessToken = contents \ "access_token"
    accessToken.as[String]
  }

  "Application" should {

    /* Play default behavior has changed here. Haven't found a good solution to this yet.
    "send 404 on a bad request" in
      running(TestUtil.app) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
     */

    "disallow requests to secure pages with missing authentication token" in
      running(TestUtil.app) {
        val secret = route(FakeRequest(GET, "/secret.html")).get
        status(secret) must throwA[UnauthorizedException]
      }

    "show token creation page" in
      running(TestUtil.app) {
        val createToken = route(FakeRequest(GET, "/requestCredentials.html")).get
        status(createToken) must equalTo(OK)
      }

    "handle bad requests for credentials" in
      running(TestUtil.app) {
        val request = FakeRequest(POST, "/requestCredentials.html") withFormUrlEncodedBody ("whatever" -> "foo")
        val tokenRequest = route(request).get
        status(tokenRequest) must equalTo(BAD_REQUEST)
      }

    "create credentials upon request" in
      running(TestUtil.app) {
        val user = java.util.UUID.randomUUID().toString() + "@met.no"
        val request = FakeRequest(POST, "/requestCredentials.html") withFormUrlEncodedBody ("email" -> user)
        val tokenRequest = route(request).get
        status(tokenRequest) must equalTo(SEE_OTHER)

        val nextUrl = redirectLocation(tokenRequest).get
        val landingPage = route(FakeRequest(GET, nextUrl)).get

        status(landingPage) must equalTo(OK)

        // This does not work: The resulting page seems to get a null flash
        // scope, or something, meaning that the client_id and client_secret
        // Is not shown in the resulting page.
        //        DB.withTransaction("authorization") { implicit conn =>
        //          val result = SQL("SELECT client_id, client_secret FROM authorized_keys WHERE email={email}")
        //            .on("email" -> user)
        //            .apply() map {
        //              row =>
        //                (row[java.util.UUID]("client_id"), row[java.util.UUID]("client_secret"))
        //            }
        //          result.size must equalTo(1)
        //          val id = result(0)._1.toString
        //          val secret = result(0)._2.toString
        //          contentAsString(landingPage) must contain(id)
        //          contentAsString(landingPage) must contain(secret)
        //        }
      }

    "accept credentials to get an access token" in
      running(TestUtil.app) {
        getAccessToken() must not beEmpty
      }

    "reject invalid credentials to get an access token" in
      running(TestUtil.app) {
        getAccessToken() must not beEmpty
      }

    "send unauthorized when sending rubbish request for bearer token" in
      running(TestUtil.app) {
        val result = route(FakeRequest(POST, "/requestAccessToken").withFormUrlEncodedBody("whatever" -> "rubbish")).get
        status(result) must throwA[BadRequestException]
      }

    "deliver different credentials to different users" in
      running(TestUtil.app) {
        getAccessToken("someone@met.no") must not equalTo getAccessToken("someone_else@met.no")
      }

    "allow access when presented with valid access token" in
      running(TestUtil.app) {
        val userToken = getAccessToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "deny access when presented with invalid access token" in
      running(TestUtil.app) {
        val body = AnyContentAsFormUrlEncoded(
          Map("grant_type" -> List("client_credentials"),
            "client_id" -> List("sga"),
            "client_secret" -> List("asgs")))

        val result = route(FakeRequest(POST, "/requestAccessToken").withBody(body)).get
        status(result) must throwA[UnauthorizedException]
    }

    "deny access when presented with invalid grant_type" in
      running(TestUtil.app) {
        val client = ClientCredentials.create("someone@met.no")
        val body = AnyContentAsFormUrlEncoded(
          Map("grant_type" -> List("client_credential"),
            "client_id" -> List(client.id),
            "client_secret" -> List(client.secret)))

        val result = route(FakeRequest(POST, "/requestAccessToken").withBody(body)).get
        status(result) must throwA[UnauthorizedException]
    }

    "allow access multiple spaces in bearer token" in
      running(TestUtil.app) {
        val userToken = getAccessToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer   $userToken"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "deny access when presented with invalid access token" in
      running(TestUtil.app) {
        val headers = FakeHeaders(List("Authorization" -> "Bearer invalidCredentials"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must throwA[UnauthorizedException]
      }

    "allow requests to secure pages with missing authentication token if turned off in config" in
      running(TestUtil.app("auth.active" -> false)) {

        val secret = route(FakeRequest(GET, "/secret.html")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "disallow requests to secure pages with missing authentication token explicitly turned on in config" in
      running(TestUtil.app("auth.active" -> true)) {

        val secret = route(FakeRequest(GET, "/secret.html")).get
        status(secret) must throwA[UnauthorizedException]
      }

    "give error when authorization config is weird" in
      running(TestUtil.app("auth.active" -> "koko?")) {

        val secret = route(FakeRequest(GET, "/secret.html")).get
        status(secret) must throwA[Exception]
      }

    "allow access to topsecret with valid access token and permissions" in
      running(TestUtil.app) {
        val userToken = getTopSecretToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val secret = route(FakeRequest(GET, "/topsecret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("This is _really_ secret!!")
      }

    "allow access to topsecret when presented with valid user access token with some permissions" in
      running(TestUtil.app) {
        val userToken = getSecretToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val secret = route(FakeRequest(GET, "/topsecret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("This is _really_ secret!!")
    }

    "deny access to topsecret when presented with invalid access token" in
      running(TestUtil.app) {
        val userToken = "f007ba11-dad5-f1ed-dead-decafc0ffee5"
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val result = route(FakeRequest(GET, "/topsecret.html", headers, "")).get
        status(result) must throwA[UnauthorizedException]
    }

    "deny access to topsecret when presented with valid user access token without permission" in
      running(TestUtil.app) {
        val userToken = getAccessToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val result = route(FakeRequest(GET, "/topsecret.html", headers, "")).get
        status(result) must throwA[UnauthorizedException]
    }

    "allow access to topsecret and show priviled info with valid access token and permissions" in
      running(TestUtil.app) {
        val userToken = getTopSecretToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val secret = route(FakeRequest(GET, "/topsecret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("You are privileged!")
      }

    "allow access to topsecret but deny privileged info when presented with valid user access token with some permission" in
      running(TestUtil.app) {
        val userToken = getSecretToken()
        val headers = FakeHeaders(List("Authorization" -> s"Bearer $userToken"))
        val secret = route(FakeRequest(GET, "/topsecret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must not contain("You are privileged!")
    }

  }
}
