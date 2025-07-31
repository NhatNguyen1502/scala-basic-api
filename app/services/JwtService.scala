package services

import pdi.jwt.{JwtAlgorithm, JwtJson}
import play.api.Configuration
import play.api.libs.json.Json

import java.time.Clock
import javax.inject.{Inject, Singleton}

@Singleton
class JwtService @Inject() (config: Configuration) {
  private val secretKey = config.get[String]("jwt.secret.key")
  private val expirationTime = config.get[Int]("jwt.expiration")
  private val algo = JwtAlgorithm.HS256
  // Implicit clock for JWT time-based claims (iat, exp)
  implicit val clock: Clock = Clock.systemUTC

  def createToken(email: String, role: String): String = {
    val now = clock.instant().getEpochSecond
    val claim = Json.obj(
      ("email", email),
      ("role", role),
      ("iat", Some(now)),
      ("exp", Some(now + expirationTime))
    )
    JwtJson.encode(claim, secretKey, algo)
  }

}
