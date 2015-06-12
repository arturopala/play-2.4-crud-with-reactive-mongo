package controllers

import models.Vessel
import play.api.i18n.Langs
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }
import play.twirl.api.Html
import services.VesselsService

class VesselsController(vesselsService: VesselsService) extends Controller {

  def vessels = Action {
    Ok("")
  }

}
