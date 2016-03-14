package controllers

import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.validation.ValidationError
import play.api.mvc._
import play.api.libs.json._
import java.util.UUID
import models.Vessel
import services.VesselsService

class VesselsController(service: VesselsService)
    extends CRUDController[Vessel, UUID](service)(routes.VesselsController.read) {

  def search = Action.async(parse.json) { implicit request =>

    val limit = request.queryString.get("limit").getOrElse(DEFAULT_LIMIT).head.toInt

    validateAndThen[services.SearchQuery] {
      query =>
        service
          .search(query, limit)
          .map(
            entity => Ok(
              Json.toJson(entity)
            )
          )
    }
  }

}