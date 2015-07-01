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
import scala.util._
import play.api.test._
import play.api.test.Helpers._
import no.met.security._
import com.github.nscala_time.time.Imports._
import com.google.common.io._

// scalastyle:off magic.number

@RunWith(classOf[JUnitRunner])
class BasicAuthSpec extends Specification {

  "BasicAuth" should {

    "identify a valid client_id" in
      running(TestUtil.app) {
        val credentials = Authorization.newClient("someone@met.no")
        val clientId = credentials.id
        val encoded = BaseEncoding.base64Url().encode(s"$clientId:".getBytes("UTF-8"))
        val headers = FakeHeaders(List("Authorization" -> s"Basic $encoded"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "should reject case-insensitive type (lower case)" in
      running(TestUtil.app) {
        val credentials = Authorization.newClient("someone@met.no")
        val clientId = credentials.id
        val encoded = BaseEncoding.base64Url().encode(s"$clientId:".getBytes("UTF-8"))
        val headers = FakeHeaders(List("Authorization" -> s"basic $encoded"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must throwA[UnauthorizedException]
      }

    "should reject case-insensitive type (ALL CAPS)" in
      running(TestUtil.app) {
        val credentials = Authorization.newClient("someone@met.no")
        val clientId = credentials.id
        val encoded = BaseEncoding.base64Url().encode(s"$clientId:".getBytes("UTF-8"))
        val headers = FakeHeaders(List("Authorization" -> s"BASIC $encoded"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must throwA[UnauthorizedException]
      }

    "should reject case-insensitive type (vArIED CAse)" in
      running(TestUtil.app) {
        val credentials = Authorization.newClient("someone@met.no")
        val clientId = credentials.id
        val encoded = BaseEncoding.base64Url().encode(s"$clientId:".getBytes("UTF-8"))
        val headers = FakeHeaders(List("Authorization" -> s"bASiC $encoded"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must throwA[UnauthorizedException]
      }

    "should ignore passphrase of credentials" in
      running(TestUtil.app) {
        val credentials = Authorization.newClient("someone@met.no")
        val clientId = credentials.id
        val encoded = BaseEncoding.base64Url().encode(s"$clientId:DUMMY-PASSWORD".getBytes("UTF-8"))
        val headers = FakeHeaders(List("Authorization" -> s"Basic $encoded"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must equalTo(OK)
        contentAsString(secret) must contain("Don't tell")
      }

    "reject an invalid client_id" in
      running(TestUtil.app) {
        val headers = FakeHeaders(List("Authorization" -> s"Basic fake-client-id"))
        val secret = route(FakeRequest(GET, "/secret.html", headers, "")).get
        status(secret) must throwA[UnauthorizedException]
      }

  }
}

// scalastyle:on
