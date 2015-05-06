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
import play.api.http.HeaderNames
import play.api.mvc.AnyContentAsFormUrlEncoded
import net.sf.ehcache.config.Configuration

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  def getAccessToken(user: String = "someone@met.no"): String = {
    val key = Authorization.newClient(user)
    val body = AnyContentAsFormUrlEncoded(
      Map("grant_type" -> List("client_credentials"),
        "client_id" -> List(key),
        "client_secret" -> List("")))

    val result = route(FakeRequest(POST, "/requestAccessToken").withBody(body)).get
    status(result) must equalTo(OK)
    val contents = contentAsJson(result)
    val accessToken = contents \ "access_token"
    accessToken.as[String]
  }

  "Application" should {

    "send 404 on a bad request" in
      running(TestUtil.app) {
        route(FakeRequest(GET, "/boum")) must beNone
      }

    "disallow requests to secure pages with missing authentication token" in
      running(TestUtil.app) {
        val secret = route(FakeRequest(GET, "/secret.html")).get
        status(secret) must equalTo(UNAUTHORIZED)
        contentAsString(secret) must not contain ("Don't tell")
      }

    "accept credentials to get an access token" in
      running(TestUtil.app) {
        getAccessToken() must not beEmpty
      }

    "deliver different credentials to different users" in
      running(TestUtil.app) {
        getAccessToken("someone@met.no") must not equalTo getAccessToken("someone_else@met.no")
      }

    "allow access when presented with valid access token" in
      running(TestUtil.app) {
        val userToken = getAccessToken()
        val headers = FakeHeaders(List("Authorization" -> List(s"Bearer $userToken")))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "allow access multiple spaces in bearer token" in
      running(TestUtil.app) {
        val userToken = getAccessToken()
        val headers = FakeHeaders(List("Authorization" -> List(s"Bearer   $userToken")))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "deny access when presented with invalid access token" in
      running(TestUtil.app) {
        val headers = FakeHeaders(List("Authorization" -> List("Bearer invalidCredentials")))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(UNAUTHORIZED)
        contentAsString(secret) must not contain ("Don't tell")
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
        status(secret) must equalTo(UNAUTHORIZED)
        contentAsString(secret) must not contain ("Don't tell")
      }

    "Give error when authorization config is weird" in
      running(TestUtil.app("auth.active" -> "koko?")) {

        val secret = route(FakeRequest(GET, "/secret.html")).get
        status(secret) must equalTo(INTERNAL_SERVER_ERROR)
        contentAsString(secret) must not contain ("Don't tell")
      }

  }
}
