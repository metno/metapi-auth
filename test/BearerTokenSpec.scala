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
import scala.util._
import no.met.security._
import com.github.nscala_time.time.Imports._

// scalastyle:off magic.number

@RunWith(classOf[JUnitRunner])
class BearerTokenSpec extends Specification {

  def createToken(milliSecondsToLive: Long = 500, userId: Long = 0): BearerToken =
    BearerToken.create(userId, Duration.millis(milliSecondsToLive))

  "BearerTokens" should {

    "be encodeable" in
      running(TestUtil.app) {
        lazy val token = createToken()

        token.encoded must not beEmpty
      }

    "isValid must give correct answer" in
      running(TestUtil.app) {
        lazy val token = createToken(100)
        token.isValid must beTrue
        Thread.sleep(100)
        token.isValid must beFalse
      }

    "be parseable" in
      running(TestUtil.app) {
        val token = createToken()
        val representation = token.encoded
        BearerToken.parse(representation) must equalTo(Success(token))
      }

    "have a parseable user" in
      running(TestUtil.app) {
        val token = createToken(userId = 1)
        val representation = token.encoded
        val parsed = BearerToken.parse(representation).get
        parsed.userId must equalTo(1)
      }

    "have a parseable user" in
      running(TestUtil.app) {
        val token = createToken(userId = 2)
        val representation = token.encoded
        val parsed = BearerToken.parse(representation).get
        parsed.userId must equalTo(2)
      }

    "reject expired tokens" in
      running(TestUtil.app) {
        val encoded = new BearerToken(0, DateTime.yesterday).encoded
        BearerToken.parse(encoded) must beFailedTry
      }

    "reject invalid tokens" in
      running(TestUtil.app) {
        var code = createToken().encoded
        code = '_' + code.substring(1)
        BearerToken.parse(code) must beFailedTry
      }

    "have a unique signature" in
      running(TestUtil.app) {
        val signatureA = createToken(50).encoded.split("\\|")(0)
        val signatureB = createToken(40).encoded.split("\\|")(0)
        signatureA must not equalTo signatureB
      }
  }
}

// scalastyle:on
