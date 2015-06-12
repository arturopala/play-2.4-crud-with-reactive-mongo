import org.junit.runner.RunWith
import org.scalatest.{ WordSpecLike, Matchers }
import org.scalatest.prop.PropertyChecks
import org.scalatest.junit.JUnitRunner
import org.scalacheck._
import play.api.libs.json._

class ModelSpec extends WordSpecLike with Matchers with PropertyChecks {

  import models._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 1024, minSuccessful = 25, workers = 5)

  val LatGenerator = Gen.chooseNum(-90d, 90d)
  val LngGenerator = Gen.chooseNum(-180d, 180d) suchThat (_ < 180)
  val TimeGenerator = Gen.chooseNum(0L, Long.MaxValue)
  val LatLngGenerator = for {
    lat <- LatGenerator
    lng <- LngGenerator
  } yield LatLng(lat, lng)

  "A Model" must {

    "provide class LatLng modelling GPS coordinates" which {
      "should be serializable to/from json array" in {
        forAll(LatGenerator, LngGenerator) { (a: Double, b: Double) =>
          val c = LatLng(a, b)
          c.latitude should be(a)
          c.longitude should be(b)
          Json.toJson(c) should be(JsArray(Seq(JsNumber(a), JsNumber(b))))
          Json.parse(s"[$a,$b]").as[LatLng] should be(c)
        }
      }
    }

    "provide class Position modelling GPS position at time" which {
      "should be serializable to/from json object" in {
        forAll(LatLngGenerator, TimeGenerator) { (c: LatLng, t: Long) =>
          val p = Position(c, t)
          p.time should be(t)
          p.location should be(c)
          Json.toJson(p) should be(JsObject(Map("location" -> Json.toJson(c), "time" -> JsNumber(t))))
          Json.parse(s"""{"time": $t, "location": ${Json.stringify(Json.toJson(c))}}""").as[Position] should be(p)
        }
      }
    }

    /*"provide class Vessel modelling ship at the sea" which {
      "should be serializable to/from json object" in {
        forAll(LatGenerator, LngGenerator, TimeGenerator) { (a: Double, b: Double, t: Long) =>
          val p = Position(LatLng(a, b), t)
          p.time should be(t)
          p.location.latitude should be(a)
          p.location.longitude should be(b)
          Json.toJson(p) should be(JsObject(Map("location" -> JsArray(Seq(JsNumber(a), JsNumber(b))), "time" -> JsNumber(t))))
          Json.parse(s"""{"time": $t, "location": [$a,$b]}""").as[Position] should be(p)
        }
      }
    }*/
  }
}