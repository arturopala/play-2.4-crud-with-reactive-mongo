package services

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

class CRUDServiceSpec extends WordSpecLike with Matchers with PropertyChecks with utils.CommonGenerators with ScalaFutures {

  import models._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 1)

  "A TestCRUDService" must {

    val identity = Vessel.VesselIdentity
    val service = new TestCRUDService[Vessel, UUID]

    "support testing creation, finding, updating and deletion of entities" in {
      forAll(VesselGenerator) { (vessel: Vessel) =>
        val id: UUID = service.create(vessel).futureValue.right.get
        service.create(vessel).futureValue.right.get should be(id)
        service.findById(id).futureValue.get.copy(uuid = None) should be(vessel)
        service.findByCriteria(Map("uuid" -> id)).futureValue.head.copy(uuid = None) should be(vessel)
        service.findByCriteria(Map("name" -> vessel.name, "width" -> vessel.width)).futureValue.head.copy(uuid = None) should be(vessel)
        val vessel2 = vessel.copy(name = vessel.name.reverse, width = vessel.width + 5, length = vessel.length - 1, uuid = None)
        service.update(id, vessel2).futureValue.right.get should be(id)
        service.findById(id).futureValue.get.copy(uuid = None) should be(vessel2)
        service.delete(id).futureValue.right.get should be(id)
        service.findById(id).futureValue shouldBe None
        service.findByCriteria(Map("name" -> vessel.name, "width" -> vessel.width)).futureValue should be('empty)
      }
    }
  }
}