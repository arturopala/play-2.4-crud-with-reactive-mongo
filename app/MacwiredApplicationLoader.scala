
import play.api.ApplicationLoader.Context
import play.api._

class MacwiredApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = {
    Logger.configure(context.environment)
    (new BuiltInComponentsFromContext(context) with Components).application
  }
}

import play.api.i18n._
import play.api.routing.Router
import router.Routes
import controllers.Assets

trait Components extends BuiltInComponents with AppComponents with I18nComponents {

  import com.softwaremill.macwire.MacwireMacros._

  lazy val assets: Assets = wire[Assets]
  lazy val router: Router = wire[Routes] withPrefix "/"

  def langs: Langs
}
