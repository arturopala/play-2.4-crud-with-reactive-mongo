import org.openqa.selenium.WebDriver
import play.api.test._
import play.api.{ ApplicationLoader, Environment, Mode }

class WithApplication extends WithApplicationLoader(new MacwiredApplicationLoader)
class WithApplicationInBrowser[W <: WebDriver]
  extends WithBrowser[W](app = new MacwiredApplicationLoader().load(ApplicationLoader.createContext(new Environment(new java.io.File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test))))
