
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
import play.api.test._
import models._

class ApplicationSpec extends WordSpecLike with Matchers with PropertyChecks with CommonGenerators with ScalaFutures {

  import Helpers._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 5)

  val UUIDRegex = """/vessels/(\w{8}-\w{4}-\w{4}-\w{4}-\w{12})""".r

  "Application" should {

    "send 404 on a bad request" in new WithTestApplication {
      val result = route(FakeRequest(GET, "/boum")).get
      status(result) should be(404)
    }

    "render the index page" in new WithTestApplication {
      val home = route(FakeRequest(GET, "/")).get
      status(home) should be(OK)
      contentType(home) shouldBe Some("text/html")
      contentAsString(home) should include("vessel")
    }

    val VESSEL_CRUD_API_TEST = { (vessel: Vessel) =>
      // Test CREATE
      val createRequest = FakeRequest(POST, "/vessels").withJsonBody(Json.toJson(vessel))
      val createResponse = route(createRequest).get
      status(createResponse) should be(CREATED)
      contentType(createResponse) shouldBe None
      contentAsString(createResponse) should be("")
      val locationHeader = header(LOCATION, createResponse).get
      locationHeader should fullyMatch regex UUIDRegex
      val UUIDRegex(uuid) = locationHeader
      // Test FIND BY ID
      val findByIdRequest = FakeRequest(GET, s"/vessels/$uuid")
      val findByIdResponse = route(findByIdRequest).get
      status(findByIdResponse) should be(OK)
      contentType(findByIdResponse) shouldBe Some("application/json")
      val storedVessel = Json.parse(contentAsString(findByIdResponse)).as[Vessel]
      storedVessel should be(vessel.copy(uuid = Some(UUID.fromString(uuid))))
      // Test FIND BY CRITERIA
      val criteria = Json.stringify(Json.toJson(vessel /*.copy(lastSeenPosition = None)*/ ))
      val findByCiteriaRequest = FakeRequest(GET, s"/vessels?query=$criteria")
      val findByCiteriaResponse = route(findByCiteriaRequest).get
      status(findByCiteriaResponse) should be(OK)
      val foundVessels = Json.parse(contentAsString(findByCiteriaResponse)).as[List[Vessel]]
      foundVessels should have size 1
      foundVessels.head should be(storedVessel)
      // Test UPDATE
      val modifiedVessel = vessel.copy(name = vessel.name.reverse, uuid = Some(UUID.randomUUID()))
      val updateRequest = FakeRequest(PUT, s"/vessels/$uuid").withJsonBody(Json.toJson(modifiedVessel))
      val updateResponse = route(updateRequest).get
      status(updateResponse) should be(OK)
      val updatedLocationHeader = header(LOCATION, updateResponse).get
      updatedLocationHeader should fullyMatch regex UUIDRegex
      val UUIDRegex(updatedUuid) = updatedLocationHeader
      updatedUuid should be(uuid)
      // Test DELETE
      val deleteRequest = FakeRequest(DELETE, s"/vessels/$uuid")
      val deleteResponse = route(deleteRequest).get
      status(deleteResponse) should be(OK)
      header(LOCATION, deleteResponse) shouldBe None
      // Test again if DELETE is idempotent
      val deleteResponse2 = route(deleteRequest).get
      status(deleteResponse2) should be(OK)
      header(LOCATION, deleteResponse2) shouldBe None
      // Test again FIND BY ID
      val findByIdResponse2 = route(findByIdRequest).get
      status(findByIdResponse2) should be(NOT_FOUND)
      // Test again FIND BY CRITERIA
      val findByCiteriaResponse2 = route(findByCiteriaRequest).get
      status(findByCiteriaResponse2) should be(OK)
      contentAsString(findByCiteriaResponse2) should be("[]")
      // Test again UPDATE
      val updateResponse2 = route(updateRequest).get
      //status(updateResponse2) should be(BAD_REQUEST) FIXME: Fails with mongolab
    }

    "provide an API to create, find, update and delete vessel - with test backend" in new WithTestApplication {
      forAll(VesselGenerator)(VESSEL_CRUD_API_TEST)
    }

    /*"provide an API to create, find, update and delete vessel - with mongolab backend" in new WithRealApplication {
      forAll(VesselGenerator)(VESSEL_CRUD_API_TEST)
    }*/

  }
}
