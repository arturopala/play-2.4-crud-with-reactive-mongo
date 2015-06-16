package controllers

import models.Vessel
import services.VesselsService

import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.json._
import java.util.UUID

class VesselsController(vesselsService: VesselsService) extends Controller {

  def one(id: UUID) = Action.async {
    vesselsService.findById(id).map(_.fold(
      NotFound(s"vessel #$id not found")
    )(vessel =>
        Ok(Json.toJson(vessel)))
    )
  }

  def selection = Action.async { implicit request =>
    (parseJsonParam("query"), parseJsonParam("sort")) match {
      case ((_, Success(query)), (_, Success(sort))) => vesselsService
        .findByCriteria(Map("$query" -> query, "$sort" -> sort))
        .map(vessels =>
          Ok(Json.toJson(vessels))
        )
      case (q, s) => Future.successful(BadRequest(toError(q) ++ toError(s)))
    }

  }

  def create = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[Vessel] { vessel =>
      vesselsService.create(vessel).map {
        case Right(id) => Created.withHeaders(LOCATION -> routes.VesselsController.one(id).url)
        case Left(err) => BadRequest(err)
      }
    }
  }

  def update(id: UUID) = Action.async(parse.json) { implicit request =>
    parseValidateAndProcess[Vessel] { vessel =>
      vesselsService.update(id, vessel).map {
        case Right(id) => Ok.withHeaders(LOCATION -> routes.VesselsController.one(id).url)
        case Left(err) => BadRequest(err)
      }
    }
  }

  def delete(id: UUID) = Action.async {
    vesselsService.delete(id).map {
      case Right(id) => Ok.withHeaders(LOCATION -> routes.VesselsController.one(id).url)
      case Left(err) => BadRequest(err)
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

  private def parseJsonParam(param: String)(implicit request: Request[Any]): (String, Try[JsValue]) = (param, Try(request.queryString.get(param).map(_.head).map(Json.parse(_)).getOrElse(Json.obj())))

  private def toError(t: (String, Try[JsValue])): JsObject = t match {
    case (paramName, Failure(e)) => Json.obj(paramName -> e.getMessage)
    case _ => Json.obj()
  }
}

