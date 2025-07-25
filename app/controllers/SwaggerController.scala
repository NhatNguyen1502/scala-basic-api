package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class SwaggerController @Inject() (cc: ControllerComponents)
    extends AbstractController(cc) {
  def ui: Action[AnyContent] = Action {
    Ok(views.html.swagger()) // render ra view chứa Swagger UI
  }
}
