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

class TestCRUDServiceSpec extends WordSpecLike with Matchers with PropertyChecks with CommonGenerators with ScalaFutures {

  import models._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 5)

  "A TestCRUDService" must {

    val identity = Vessel.VesselIdentity
    val service = new TestCRUDService[Vessel, UUID]

    "allow testing creation, finding, updating and deletion of entities" in {
      forAll(VesselGenerator) { (vessel: Vessel) =>
        // should create new entity and assign id
        val id: UUID = service.create(vessel).futureValue.right.get
        // should not create another entity with same values except id
        service.create(vessel).futureValue shouldBe Right(id)
        // should find existing entity by id
        service.findById(id).futureValue.get.copy(uuid = None) should be(vessel)
        // should find existing entity by id criteria
        val found = service.findByCriteria(Map("uuid" -> id), 2).futureValue
        found should have size 1 // uuid must be unique
        found.head.copy(uuid = None) should be(vessel)
        // should find existing entity by multiple criteria
        service.findByCriteria(Map("name" -> vessel.name, "width" -> vessel.width, "length" -> vessel.length), 2).futureValue.head.copy(uuid = None) should be(vessel)
        // should find existing entity by json query
        service.findByCriteria(Map("$query" -> Json.toJson(vessel)), 2).futureValue.head.copy(uuid = None) should be(vessel)
        // create variant of vessel
        val vessel2 = vessel.copy(name = vessel.name.reverse, width = vessel.width + 5, length = vessel.length - 1, uuid = None)
        // should update existing entity
        service.update(id, vessel2).futureValue shouldBe Right(id)
        // should find by id after update (update must not change id)
        service.findById(id).futureValue.get.copy(uuid = None) should be(vessel2)
        // should confirm deletion by returning id
        service.delete(id).futureValue shouldBe Right(id)
        // should not find by id when removed
        service.findById(id).futureValue shouldBe None
        // should not find by criteria when removed
        service.findByCriteria(Map("name" -> vessel.name, "width" -> vessel.width, "length" -> vessel.length), 2).futureValue should be('empty)
        // should confirm deletion even when removed (idempotent)
        service.delete(id).futureValue shouldBe Right(id)
        // should not confirm update nor create new when removed
        service.update(id, vessel).futureValue should be('left)
        // should not find by id when removed (even after update)
        service.findById(id).futureValue shouldBe None
        // should assign different id when re-created
        service.create(vessel).futureValue should not be Right(id)
        // should not find by id after re-creation
        service.findById(id).futureValue shouldBe None
      }
    }
  }

  "A CriteriaJSONWriter" must {
    import play.api.libs.json._
    import services.CriteriaJSONWriter

    "serialize criteria to bson" in {
      CriteriaJSONWriter.writes(Map("id" -> 5)) should be(Json.obj("id" -> 5))
      CriteriaJSONWriter.writes(Map("id" -> "abc")) should be(Json.obj("id" -> "abc"))
      CriteriaJSONWriter.writes(Map("id" -> 473473278747324L)) should be(Json.obj("id" -> 473473278747324L))
      CriteriaJSONWriter.writes(Map("id" -> 678.7634734)) should be(Json.obj("id" -> 678.7634734))
      CriteriaJSONWriter.writes(Map("id" -> false)) should be(Json.obj("id" -> false))
      CriteriaJSONWriter.writes(Map("$query" -> Json.obj("id" -> Json.obj("$regex" -> "sd.*?")))) should be(Json.obj("$query" -> Json.obj("id" -> Json.obj("$regex" -> "sd.*?"))))
    }

  }
}