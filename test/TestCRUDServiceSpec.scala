package services

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import play.api.libs.json._
import play.api.libs.json.Json._

import org.junit.runner.RunWith
import org.scalatest.{ WordSpecLike, Matchers }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.PropertyChecks
import org.scalatest.junit.JUnitRunner
import org.scalacheck._
import utils._

class TestCRUDServiceSpec extends WordSpecLike with Matchers with PropertyChecks with CommonGenerators with ScalaFutures {

  import models._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 5)

  "A TestCRUDService" must {

    val identity = Vessel.VesselIdentity

    /*"allow testing creation, finding, updating and deletion of entities" in {
      forAll(VesselGenerator) { (vessel: Vessel) =>
        
        // should find existing entity by id
        service.read(id).futureValue.get.copy(uuid = None) should be(vessel)
        // should find existing entity by id criteria
        val found = service.search(Json.obj("uuid" -> id), 2).futureValue
        found should have size 1 // uuid must be unique
        found.head.copy(uuid = None) should be(vessel)
        // should find existing entity by multiple criteria
        service.search(Json.obj("name" -> vessel.name, "width" -> vessel.width, "length" -> vessel.length), 2).futureValue.head.copy(uuid = None) should be(vessel)
        // should find existing entity by json query
        service.search(Json.toJson(vessel).as[JsObject], 2).futureValue.head.copy(uuid = None) should be(vessel)
        // create variant of vessel
        val vessel2 = vessel.copy(name = vessel.name.reverse, width = vessel.width + 5, length = vessel.length - 1, uuid = None)
        // should update existing entity
        service.update(id, vessel2).futureValue shouldBe Right(id)
        // should find by id after update (update must not change id)
        service.read(id).futureValue.get.copy(uuid = None) should be(vessel2)
        // should confirm deletion by returning id
        service.delete(id).futureValue shouldBe Right(id)
        // should not find by id when removed
        service.read(id).futureValue shouldBe None
        // should not find by criteria when removed
        service.search(Json.obj("name" -> vessel.name, "width" -> vessel.width, "length" -> vessel.length), 2).futureValue should be('empty)
        // should confirm deletion even when removed (idempotent)
        service.delete(id).futureValue shouldBe Right(id)
        // should not confirm update nor create new when removed
        service.update(id, vessel).futureValue should be('left)
        // should not find by id when removed (even after update)
        service.read(id).futureValue shouldBe None
        // should assign different id when re-created
        service.create(vessel).futureValue should not be Right(id)
        // should not find by id after re-creation
        service.read(id).futureValue shouldBe None
      }
    }*/

    "not find existing instance for new entity" in {
      val service = new TestCRUDService[Vessel, UUID]
      forAll(VesselGenerator) { (vessel: Vessel) =>
        service.search(Json.toJson(vessel).as[JsObject], 1).futureValue shouldBe empty
      }
    }

    "create new entitity" in {
      val service = new TestCRUDService[Vessel, UUID]
      forAll(VesselGenerator) { (vessel: Vessel) =>
        val id: UUID = service.create(vessel).futureValue.right.get
        id should not be null
      }
    }

    "create entity only once for the same id" in {
      val service = new TestCRUDService[Vessel, UUID]
      forAll(VesselGenerator) { (vessel: Vessel) =>
        val id: UUID = service.create(vessel).futureValue.right.get
        service.create(vessel).futureValue shouldBe Right(id)
      }
    }

    "find existing entity by id" in {
      val service = new TestCRUDService[Vessel, UUID]
      forAll(VesselGenerator) { (vessel: Vessel) =>
        val id: UUID = service.create(vessel).futureValue.right.get
        service.read(id).futureValue.get.copy(uuid = None) shouldBe vessel
      }
    }
  }
}
