package controllers

import play.api.i18n.Langs
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }
import play.twirl.api.Html

class MainController extends Controller {

  def index = Action {
    Ok(Html("<h1>Welcome</h1><p>Your new application is ready.</p>"))
  }

}
