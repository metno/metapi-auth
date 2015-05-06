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
import org.specs2.time.NoDurationConversions
import org.specs2.time.NoTimeConversions
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import com.github.nscala_time.time.Imports._
import no.met.security._

@RunWith(classOf[JUnitRunner])
class AuthorizationSpec extends Specification with NoTimeConversions {

  "Authorization object" should {
    "create authorization keys" in
      running(TestUtil.app) {
        Authorization.newClient("someone@met.no") must not beEmpty
      }

    "create different keys for different addresses" in
      running(TestUtil.app) {
        val clientA = Authorization.newClient("someone@met.no")
        val clientB = Authorization.newClient("someoneelse@met.no")
        clientA must !==(clientB)
      }

    "create same key when requested several times for same address" in
      running(TestUtil.app) {
        val clientA = Authorization.newClient("someone@met.no")
        val clientB = Authorization.newClient("someone@met.no")
        clientA must be equalTo clientB
      }

    "authenticate previously created keys" in
      running(TestUtil.app) {
        val clientId = Authorization.newClient("someone@met.no")
        Authorization.authorized(AccessTokenRequest("client_credentials", clientId, "")) must not beFailedTry
      }

    "not authenticate random keys" in
      running(TestUtil.app) {
        Authorization.authorized(AccessTokenRequest("client_credentials", "invalid-clientId", "")) must beFailedTry
      }

    "not authenticate NULL keys" in
      running(TestUtil.app) {
        Authorization.authorized(null) must beFailedTry // scalastyle:ignore null
      }

    /**
     * Generates a valid access token, for testing
     */
    def getBearerToken(user: String = "someone@met.no", milliSecondsToLive: Long = 500): String = { // scalastyle:ignore magic.number
      val clientId = Authorization.newClient(user)
      val accessRequest = AccessTokenRequest("client_credentials", clientId, "")
      Authorization.generateBearerToken(accessRequest, Duration.millis(milliSecondsToLive)).getOrElse("")
    }

    "generate bearer tokens" in
      running(TestUtil.app) {
        getBearerToken() must not beEmpty
      }

    "generate different bearer tokens for different users" in
      running(TestUtil.app) {
        getBearerToken() must not equalTo getBearerToken("someone_else@met.no")
      }

    "accept valid bearer tokens" in
      running(TestUtil.app) {
        val token = getBearerToken()
        Authorization.validateBearerToken(token) must beTrue
      }

    "reject invalid bearer tokens" in
      running(TestUtil.app) {
        val token = getBearerToken() + "a"
        Authorization.validateBearerToken(token) must beFalse
      }
  }
}
