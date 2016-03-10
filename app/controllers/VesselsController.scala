package controllers

import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.json._
import java.util.UUID
import models.Vessel
import services.VesselsService

class VesselsController(vesselsService: VesselsService)
    extends CRUDController[Vessel, UUID](vesselsService)(routes.VesselsController.read) {

  def search = Action.async { implicit request =>
    (parseJsonParam("query"), parseJsonParam("sort")) match {
      case ((_, Success(query)), (_, Success(sort))) => service
        .search(Map("$query" -> query, "$sort" -> sort), request.queryString.get("limit").getOrElse(DEFAULT_LIMIT).head.toInt)
        .map(entity =>
          Ok(Json.toJson(entity))
        )
      case (q, s) => Future.successful(BadRequest(toError(q) ++ toError(s)))
    }

  }

}

