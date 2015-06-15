package controllers

import models.Vessel
import services.VesselsService

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.json._

class VesselsController(vesselsService: VesselsService) extends Controller {

  def one(id: String) = Action.async {
    vesselsService.findById(id).map(_.fold(
      NotFound(s"vessel #$id not found")
    )(vessel =>
        Ok(Json.toJson(vessel)))
    )
  }

  def all = Action.async {
    vesselsService.findByCriteria(Json.obj()).map(vessels =>
      Ok(Json.toJson(vessels))
    )
  }

  def create = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[Vessel] { vessel =>
      vesselsService.create(vessel).map {
        case Right(id) => Created.withHeaders(LOCATION -> routes.VesselsController.one(id).url)
        case Left(err) => BadRequest(err)
      }
    }
  }

  def update(id: String) = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[Vessel] { vessel =>
      vesselsService.update(id, vessel).map {
        case Right(id) => Ok.withHeaders(LOCATION -> routes.VesselsController.one(id).url)
        case Left(err) => BadRequest(err)
      }
    }
  }

  private def parseValidateAndProcess[T: Reads](t: T => Future[Result])(implicit request: Request[JsValue]) = {
    request.body.validate[T].map(t) match {
      case JsSuccess(result, _) => result
      case JsError(err) => Future.successful(BadRequest(Json.toJson(err.map {
        case (path, errors) => Json.obj("path" -> path.toString, "errors" -> JsArray(errors.flatMap(e => e.messages.map(JsString(_)))))
      })))
    }
  }

}

