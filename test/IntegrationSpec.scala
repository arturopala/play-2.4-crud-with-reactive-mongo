import org.scalatest._
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._
import org.scalatest.selenium.WebBrowser
import play.api.{ Application, ApplicationLoader, Environment, Mode, BuiltInComponents, BuiltInComponentsFromContext, Logger }
import play.api.ApplicationLoader.Context

class IntegrationSpec extends PlaySpec with OneServerPerSuite with OneBrowserPerSuite with FirefoxFactory {

  override lazy val app = new TestApplicationLoader().load(ApplicationLoader.createContext(new Environment(new java.io.File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)))

  "Application" should {

    "work from within a browser" in {
      go to ("http://localhost:" + port)
      pageTitle must be("Vessels Mngmt Tool")
    }

    "search for vessels by name" in {
      go to ("http://localhost:" + port)
      click on "vesselname"
      enter("T")
      click on "searchvessels"
    }
  }
}
