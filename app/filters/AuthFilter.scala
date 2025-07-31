package filters

import pdi.jwt.{Jwt, JwtAlgorithm}
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc._

import javax.inject._
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.matching.Regex
import scala.util.{Failure, Success}

/**
 * AuthFilter:
 *   - A global filter that checks authentication and authorization using JWT
 *     tokens.
 *   - Supports path-based role authorization similar to Spring Security
 *     (patterns like `/admin/ **`, `/api/ **`).
 *   - Public endpoints can bypass authentication.
 *
 * Example configuration in `application.conf`:
 * {{{
 *   authorization {
 *     "/admin/ **" = ["ADMIN"]
 *     "/api/ *"    = ["USER", "ADMIN"]
 *     "/api/users" = ["USER"]
 *   }
 * }}}
 * Note: In code delete space character in front of `*` because `*` cannot be
 * used safely inside Scaladoc.
 */

@Singleton
class AuthFilter @Inject() (
  config: Configuration
) extends EssentialFilter {

  // List of endpoints that do not require authentication
  private val publicPaths: Set[String] = Set(
    "/api/login"
  )

  private val secretKey = config.get[String]("jwt.secret.key")
  // Loads path-to-role mappings from configuration
  private val pathRoleMappings: Seq[(Regex, Seq[String])] = {
    val authConfig = config.underlying.getConfig("authorization")
    authConfig.entrySet().asScala.toSeq.map {
      entry =>
        val rawKey = entry.getKey.stripPrefix("\"").stripSuffix("\"")
        val pathPattern = antPatternToRegex(rawKey)
        val roles = authConfig.getStringList(entry.getKey).asScala.toSeq
        (pathPattern, roles)
    }
  }

  /**
   * The `apply` method of EssentialFilter:
   *   - Takes an EssentialAction (the controller action)
   *   - Returns a new EssentialAction with filter logic
   */
  override def apply(next: EssentialAction): EssentialAction = EssentialAction {
    request =>
      // Allow public endpoints to bypass authentication
      if (isPublic(request)) {
        next(request)
      } else {
        // Extract token from Authorization header
        extractToken(request) match {
          case Some(token)
              if Jwt.isValid(token, secretKey, Seq(JwtAlgorithm.HS256)) =>
            Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS256)) match {
              case Success(claim) =>
                val json = Json.parse(claim.content)
                // Get role from token
                val role = (json \ "role").asOpt[String].getOrElse("")
                // Get accepted role in configuration
                val allowed = allowedRoles(request.path)
                // Allow access if no rule is defined or if the role is in the allowed list
                if (allowed.isEmpty || allowed.contains(role)) {
                  next(request)
                } else {
                  Accumulator.done(
                    Results.Forbidden(
                      Json.obj(
                        "success" -> "false",
                        "message" -> "Permission denied"
                      )
                    )
                  )
                }
              // Token cannot be decoded
              case Failure(_) =>
                Accumulator.done(
                  Results.Unauthorized(
                    Json.obj("success" -> "false", "message" -> "Invalid token")
                  )
                )
            }

          case _ =>
            // Invalid or missing token -> return 401 Unauthorized immediately
            // Accumulator.done() stops request processing early (no request body parsing)
            Accumulator.done(
              Results.Unauthorized(
                Json.obj(
                  "success" -> "false",
                  "message" -> "Invalid or missing token"
                )
              )
            )
        }
      }
  }

  /** Check if request path is in the list of public endpoints */
  private def isPublic(request: RequestHeader): Boolean = {
    val path = request.path
    publicPaths.contains(path) ||
    path.startsWith("/docs/swagger") ||
    path.startsWith("/assets") ||
    path.startsWith("/auth")
  }

  /** Extract JWT token from Authorization header */
  private def extractToken(request: RequestHeader): Option[String] =
    request.headers.get("Authorization").flatMap {
      header =>
        if (header.startsWith("Bearer ")) Some(header.substring(7)) else None
    }

  /**
   * Returns the list of roles allowed to access the given path.
   *   - Matches path against all configured regex patterns
   *   - Returns roles of the most specific pattern (longest regex)
   */
  private def allowedRoles(path: String): Seq[String] = {
    val matched = pathRoleMappings.filter {
      case (pattern, _) =>
        pattern.pattern.matcher(path).matches()
    }
    // Prefer the most specific rule (longest regex)
    matched.sortBy(-_._1.regex.length).headOption.map(_._2).getOrElse(Seq.empty)
  }

  /**
   * Converts a Spring-style Ant path pattern to a Scala regex:
   *   - `**` -> `.*` (matches multiple path segments)
   *   - `*` -> `[^/]*` (matches a single path segment)
   *   - Escapes `.` characters to avoid unintended regex wildcards
   */
  private def antPatternToRegex(pattern: String): Regex = {
    val placeholder = "___ANT_DOUBLE_STAR___"
    val escaped = pattern
      .replace("**", placeholder) // temporary placeholder for **
      .replace(".", "\\.") // escape dot
      .replace("*", "[^/]*") // replace single *
      .replace(placeholder, ".*") // restore **
    ("^" + escaped + "$").r
  }
}
