package repositories

import dtos.request.user.UpdateUserRequestDto
import dtos.response.user.UserResponseDto
import models.Tables.{roles, users}
import models.User
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.LocalDateTime
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserRepository @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  def create(user: User): Future[User] = {
    // db is provided by HasDatabaseConfigProvider
    db.run(users += user) // Future[Int] . success = 1, fail = 0
      .map(
        _ => user
      ) // convert to Future[User]
  }

  def existByEmail(email: String): Future[Boolean] = {
    val query = users.filter(_.email === email).exists.result
    db.run(query)
  }

  def list(): Future[Seq[UserResponseDto]] = {
    db.run(
      users
        .map(
          u => (u.id, u.email, u.age, u.isActive, u.createdAt, u.updatedAt)
        ) // Projection
        .sortBy(_._5.desc)
        .result
    ).map(_.map(UserResponseDto.tupled))
  }

  def findById(id: UUID): Future[Option[UserResponseDto]] = {
    db.run(
      users
        .filter(_.id === id)
        .map(
          u => (u.id, u.email, u.age, u.isActive, u.createdAt, u.updatedAt)
        )
        .result
        .headOption
    ).map(_.map(UserResponseDto.tupled))
  }

  def findUserWithRoleByEmail(email: String): Future[Option[(User, String)]] = {
    val query = for {
      u <- users if u.email === email
      r <- roles if r.id === u.roleId
    } yield (u, r.name)

    db.run(query.result.headOption)
  }

  def findPasswordByEmail(email: String): Future[Option[String]] = {
    db.run(
      users
        .filter(_.email === email)
        .map(
          u => (u.password)
        )
        .result
        .headOption
    )
  }

  def update(id: UUID, dto: UpdateUserRequestDto): Future[Int] = {
    // Build list dynamic update
    val updateQuery = users.filter(_.id === id)

    val updates = Seq(
      dto.age.map(
        a => updateQuery.map(_.age).update(Some(a))
      ),
      dto.isActive.map(
        a => updateQuery.map(_.isActive).update(a)
      )
    ).flatten

    // If no field to update -> return 0
    if (updates.isEmpty) {
      Future.successful(-1)
    } else {
      // Always update updatedAt
      val updatedAtAction =
        updateQuery.map(_.updatedAt).update(LocalDateTime.now())

      // Run all update sql in transaction
      val actions = DBIO.sequence(updates :+ updatedAtAction).map(_.sum)
      db.run(actions.transactionally)
    }
  }

  def delete(id: UUID): Future[Int] = {
    val query = users.filter(_.id === id).delete
    db.run(query)
  }

  def hasAnyData: Future[Boolean] = {
    val query = users.exists
    db.run(query.result)
  }
}
