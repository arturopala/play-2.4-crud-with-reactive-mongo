import org.junit.runner.RunWith
import org.scalatest.{ WordSpecLike, Matchers }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.junit.JUnitRunner
import org.scalacheck._
import play.api.libs.json._
import play.api.libs.json.Json._
import java.util.UUID
import scala.concurrent.duration.Duration

class CRUDControllerSpec extends WordSpecLike with Matchers with PropertyChecks with CommonGenerators with ScalaFutures {

  import models._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 5)

  "A CRUDController" must {

    val identity = Vessel.VesselIdentity
    val service = new TestCRUDService[Vessel, UUID]

    "support testing creation, finding, updating and deletion of entities" in {
      forAll(VesselGenerator) { (vessel: Vessel) =>

      }
    }
  }
}