package controllers

import java.util.UUID
import models.Vessel
import services.VesselsService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.json._
import scala.util.{ Try, Success, Failure }
import scala.concurrent.Future

class VesselsController(vesselsService: VesselsService)
    extends CRUDController[Vessel, UUID](vesselsService)(routes.VesselsController.read) {

  def search(name: String) = Action.async { implicit request =>
    val query = Json.obj("name" -> Json.obj("$regex" -> ("^" + name + ".*"), "$options" -> "i"))
    service
      .search(query, request.queryString.get("limit").getOrElse(DEFAULT_LIMIT).head.toInt)
      .map(entity =>
        Ok(Json.toJson(entity))
      )
  }

}

