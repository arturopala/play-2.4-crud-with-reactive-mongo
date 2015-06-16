import org.openqa.selenium.WebDriver
import play.api.test._
import play.api.{ Application, ApplicationLoader, Environment, Mode, BuiltInComponents, BuiltInComponentsFromContext, Logger }
import play.api.ApplicationLoader.Context

class WithApplication extends WithApplicationLoader(new TestApplicationLoader)
class WithApplicationInBrowser[W <: WebDriver]
  extends WithBrowser[W](app = new TestApplicationLoader().load(ApplicationLoader.createContext(new Environment(new java.io.File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test))))

class TestApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = {
    Logger.configure(context.environment)
    (new BuiltInComponentsFromContext(context) with Components with TestAppComponents).application
  }
}
