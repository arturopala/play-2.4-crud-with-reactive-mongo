package controllers

import java.util.UUID

import config.WithTestApplication
import models._
import org.scalacheck._
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.PropertyChecks
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.test._

class ApplicationSpec extends WordSpecLike with Matchers with PropertyChecks with utils.CommonGenerators with ScalaFutures {

  import Helpers._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 1)

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

    "provide an API to create vessel - mocked db" in new WithTestApplication {
      forAll(VesselGenerator) {
        vessel: Vessel =>
          val createRequest = FakeRequest(POST, "/vessels").withJsonBody(Json.toJson(vessel))
          val createResponse = route(createRequest).get
          status(createResponse) should be(CREATED)
          contentType(createResponse) shouldBe None
          contentAsString(createResponse) should be("")
          val locationHeader = header(LOCATION, createResponse).get
          locationHeader should fullyMatch regex UUIDRegex
          val UUIDRegex(uuid) = locationHeader
      }
    }

    "provide an API to read vessel - mocked db" in new WithTestApplication {
      forAll(VesselGenerator) {
        vessel: Vessel =>
          createVessel(vessel) map { uuid =>
            val findByIdRequest = FakeRequest(GET, s"/vessels/$uuid")
            val findByIdResponse = route(findByIdRequest).get
            status(findByIdResponse) should be(OK)
            contentType(findByIdResponse) shouldBe Some("application/json")
            val storedVessel = Json.parse(contentAsString(findByIdResponse)).as[Vessel]
          }
      }
    }

    "provide an API to update vessel - mocked db" in new WithTestApplication {
      forAll(VesselGenerator) {
        vessel: Vessel =>
          createVessel(vessel) map { uuid =>
            val findByIdRequest = FakeRequest(GET, s"/vessels/$uuid")
            val findByIdResponse = route(findByIdRequest).get
            status(findByIdResponse) should be(OK)
            contentType(findByIdResponse) shouldBe Some("application/json")
            val storedVessel = Json.parse(contentAsString(findByIdResponse)).as[Vessel]
            storedVessel should be(vessel.copy(uuid = Some(uuid)))
          }
      }
    }

    "provide an API to delete vessel - mocked db" in new WithTestApplication {
      forAll(VesselGenerator) {
        vessel: Vessel =>
          createVessel(vessel) map { uuid =>
            val deleteRequest = FakeRequest(DELETE, s"/vessels/$uuid")
            val deleteResponse = route(deleteRequest).get
            status(deleteResponse) should be(OK)
            header(LOCATION, deleteResponse) shouldBe None
            // Test again if DELETE is idempotent
            val deleteResponse2 = route(deleteRequest).get
            status(deleteResponse2) should be(OK)
            header(LOCATION, deleteResponse2) shouldBe None
            // Test again FIND BY ID
            val findByIdResponse = route(FakeRequest(GET, s"/vessels/$uuid")).get
            status(findByIdResponse) should be(NOT_FOUND)
          }
      }
    }

    "provide an API to search vessels - mocked db" in new WithTestApplication {
      forAll(VesselGenerator) {
        vessel: Vessel =>
          createVessel(vessel) map { uuid =>
            val query = Json.toJson(services.SearchQuery(name = Some(vessel.name.drop(1)), width = None, length = None, draft = None))
            val findByCiteriaRequest = FakeRequest(POST, s"/vessels/search").withJsonBody(query)
            val findByCiteriaResponse = route(findByCiteriaRequest).get
            status(findByCiteriaResponse) should be(OK)
            val foundVessels = Json.parse(contentAsString(findByCiteriaResponse)).as[List[Vessel]]
            foundVessels.size should be >= (1)
            foundVessels should contain(vessel.copy(uuid = Some(uuid)))
          }
      }
    }

  }

  def createVessel(vessel: Vessel): Option[UUID] = {
    route(FakeRequest(POST, "/vessels").withJsonBody(Json.toJson(vessel)))
      .flatMap(header(LOCATION, _))
      .map(s => { val UUIDRegex(uuid) = s; UUID.fromString(uuid) })
  }
}
