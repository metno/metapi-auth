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
import com.github.nscala_time.time.Imports._
import no.met.security._

@RunWith(classOf[JUnitRunner])
class AuthorizationSpec extends Specification {

  "Authorization object" should {
    "create authorization keys" in
      running(TestUtil.app) {
        val client = ClientCredentials.create("someone@met.no")
        ((client.id) must not beEmpty)
        ((client.secret) must not beEmpty)
      }

    "create different keys for different addresses" in
      running(TestUtil.app) {
        val clientA = ClientCredentials.create("someone@met.no")
        val clientB = ClientCredentials.create("someoneelse@met.no")
        clientA must !==(clientB)
      }

    "create different key when requested several times for same address" in
      running(TestUtil.app) {
        val clientA = ClientCredentials.create("someone@met.no")
        val clientB = ClientCredentials.create("someone@met.no")
        clientA must !==(clientB)
      }

    "authenticate previously created keys" in
      running(TestUtil.app) {
        val clientA = ClientCredentials.create("someone@met.no")
        val clientB = ClientCredentials.create("someone@met.no")

        clientA must !==(clientB)

        val tokenB = AccessTokenRequest("client_credentials", clientB.id, clientB.secret)
        val tokenA = AccessTokenRequest("client_credentials", clientA.id, clientA.secret)

        tokenB.generateBearerToken() must beSuccessfulTry
        tokenA.generateBearerToken() must beSuccessfulTry
      }

    "not authenticate random keys" in
      running(TestUtil.app) {
        val token = AccessTokenRequest("client_credentials", "invalid-clientId", "")
        token.generateBearerToken() must beFailedTry
      }

    //    "not authenticate NULL keys" in
    //      running(TestUtil.app) {
    //        ClientCredentials.authorized(null) must beFailedTry // scalastyle:ignore null
    //      }

    /**
     * Generates a valid access token, for testing
     */
    def getBearerToken(user: String = "someone@met.no", milliSecondsToLive: Long = 500): String = { // scalastyle:ignore magic.number
      val client = ClientCredentials.create(user)
      val accessRequest = AccessTokenRequest("client_credentials", client.id, client.secret)
      //accessRequest.generateBearerToken(Duration.millis(milliSecondsToLive)).getOrElse("TEST ERROR")
      accessRequest.generateBearerToken(Duration.millis(milliSecondsToLive)) match {
        case util.Success(r) => r
        case util.Failure(x) => {
          x.printStackTrace()
          "ERROR"
        }
      }
    }

    "generate bearer tokens" in
      running(TestUtil.app) {
        getBearerToken() must not beEmpty
      }

    "not allow empty client_secret" in
      running(TestUtil.app) {
        val client = ClientCredentials.create("someone@met.no")
        val accessRequest = AccessTokenRequest("client_credentials", client.id, "")
        val token = accessRequest.generateBearerToken(Duration.millis(500)) // scalastyle:ignore magic.number
        token must beFailedTry
      }

    "generate different bearer tokens for different users" in
      running(TestUtil.app) {
        getBearerToken() must not equalTo getBearerToken("someone_else@met.no")
      }

    "accept valid bearer tokens" in
      running(TestUtil.app) {
        val token = getBearerToken()
        Authentication.identifyUser(s"Bearer $token") must not(throwA[Exception]) //beTrue
      }

    "not validate expired tokens" in
      running(TestUtil.app) {
        val encoded = new BearerToken(0, DateTime.yesterday).encoded
        Authentication.identifyUser(s"Bearer $encoded") must throwA[UnauthorizedException] //beFalse
      }

    "accept Authorization request with bearer token" in
      running(TestUtil.app) {
        val token = getBearerToken()
        Authentication.identifyUser(s"Bearer $token") must not(throwA[Exception]) //beTrue
      }

    "reject Authorization request with bearer token" in
      running(TestUtil.app) {
        val token = getBearerToken()
        Authentication.identifyUser(s"$token") must throwA[UnauthorizedException] //beFalse
      }

    "reject invalid bearer tokens" in
      running(TestUtil.app) {
        val token = getBearerToken() + "a"
        Authentication.identifyUser(s"Bearer $token") must throwA[UnauthorizedException] //beFalse
      }
  }
}
