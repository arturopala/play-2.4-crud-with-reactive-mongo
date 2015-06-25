import org.specs2.mutable._

class IntegrationSpec extends Specification {

  "Application" should {

    "work from within a browser" in new WithTestApplicationInBrowser {
      browser.goTo("http://localhost:" + port)
      browser.pageSource must contain("vessel")
    }
  }
}
