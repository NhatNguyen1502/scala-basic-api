package services

import dtos.request.auth.{LoginRequestDto, SignUpRequestDto}
import dtos.request.user.CreateUserRequestDto
import dtos.response.auth.LoginResponseDto
import dtos.response.user.UserResponseDto
import exceptions.{AppException, ErrorCode}
import org.mindrot.jbcrypt.BCrypt
import play.api.http.Status
import repositories.UserRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject() (
  userRepository: UserRepository,
  userService: UserService,
  jwtService: JwtService
)(implicit
  ec: ExecutionContext
) {

  def authenticate(
    loginRequestDto: LoginRequestDto
  ): Future[LoginResponseDto] = {
    userRepository.findUserWithRoleByEmail(loginRequestDto.email).map {
      case Some((user, role))
          if BCrypt.checkpw(loginRequestDto.password, user.password) =>
        val accessToken = jwtService.createToken(user.email, role)
        val refreshToken = accessToken // temporary refreshToken
        LoginResponseDto(
          accessToken,
          refreshToken,
          UserResponseDto.fromUser(user)
        )

      case Some(_) =>
        // Wrong Password
        throw new AppException(ErrorCode.WrongPassword, Status.UNAUTHORIZED)

      case None =>
        // Email not found
        throw new AppException(ErrorCode.EmailNotFound, Status.UNAUTHORIZED)
    }
  }

  def signUp(request: SignUpRequestDto): Future[Unit] = {
    for {
      hasAnyData <- userRepository.hasAnyData
      _ <- {
        val role = if (hasAnyData) "USER" else "ADMIN"
        val createUserDto = CreateUserRequestDto(
          email = request.email,
          password = request.password,
          firstName = request.firstName,
          lastName = request.lastName,
          address = request.address,
          phoneNumber = request.phoneNumber,
          role = role,
          age = None
        )
        userService.createUser(createUserDto)
      }
    } yield ()
  }
}
