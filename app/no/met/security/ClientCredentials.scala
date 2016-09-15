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

import play.api.Play.current
import play.api.db._
import anorm._
import com.github.nscala_time.time.Imports._
import scala.language.postfixOps
import scala.util._
import java.util.UUID

case class ClientCredentials(id: String, secret: String) {

  def user: Try[MetApiUser] = Try {
    DB.withConnection("authorization") { implicit conn =>
      val id = userId()
      val permissions = userPermissions(id)
      new MetApiUser(id, permissions)
    }
  }

  private def userId()(implicit conn: java.sql.Connection): Long = {
    val testEmail = if (play.api.Play.isDev(play.api.Play.current) || play.api.Play.isTest(play.api.Play.current)) { "" } else { "root@localhost" }
    SQL("SELECT owner_id FROM authorized_keys WHERE client_id={id}::uuid AND client_secret={secret}::uuid AND active='true' AND email <> {email}")
      .on("id" -> id, "secret" -> secret, "email" -> testEmail)
      .as(SqlParser.long(1).singleOpt) match {
        case None => throw new NoSuchElementException(s"Unsuccessful authentication attempt with client_id ${id}")
        case Some(x) => x
      }
  }

  private def userPermissions(id: Long)(implicit conn: java.sql.Connection): Set[Int] = {
    SQL("SELECT permission FROM user_permissions WHERE owner_id={id}").on("id" -> id)
      .as(SqlParser.int(1).*)
      .toSet
  }
}

object ClientCredentials {

  /**
   * Create new client credentials, associated with the given email address.
   * The credentials may be used when calling generateBearerToken and
   * validateToken.
   *
   * @param userEmail The email address the generated key should be associated with
   * @return Valid credentials, associated with the given email address
   */
  def create(userEmail: String): ClientCredentials = {
    DB.withTransaction("authorization") { implicit conn =>

      val id = createUniqueKey()
      val secret = createUniqueKey()
      SQL("INSERT INTO authorized_keys (client_id, client_secret, email) VALUES ({client_id}::uuid, {client_secret}::uuid, {email})")
        .on("client_id" -> id, "client_secret" -> secret, "email" -> userEmail)
        .executeInsert()
      ClientCredentials(id toString, secret toString)
    }
  }

  /**
   * Identify user based on client_id alone. The returned user will always
   * have an empty permissions list.
   */
  def simpleIdentify(clientId: String): MetApiUser = {
    Try(UUID.fromString(clientId)) match {
      case util.Success(u) => {
        val testEmail = if (play.api.Play.isDev(play.api.Play.current) || play.api.Play.isTest(play.api.Play.current)) { "" } else { "root@localhost" }
        DB.withConnection("authorization") { implicit conn =>
          SQL("SELECT owner_id FROM authorized_keys WHERE client_id={id}::uuid AND active='true' AND email <> {email}")
            .on("id" -> clientId, "email" -> testEmail)
            .as(SqlParser.long(1).singleOpt) match {
              case None => throw new UnauthorizedException("Invalid basic authentication")
              case Some(x) => new MetApiUser(x, Set.empty[Int])
            }
        }
      }
      case Failure(x) => throw new UnauthorizedException("Invalid basic authentication") // UUID was not parseable
    }
  }

  private def createUniqueKey() = UUID.randomUUID

  private implicit def uuidToStatement = new ToStatement[UUID] {
    def set(s: java.sql.PreparedStatement, index: Int, aValue: UUID): Unit = s.setObject(index, aValue)
  }
}
