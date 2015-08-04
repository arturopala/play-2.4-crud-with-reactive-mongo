import org.junit.runner.RunWith
import org.scalatest.{ WordSpecLike, Matchers }
import org.scalatest.prop.PropertyChecks
import org.scalatest.junit.JUnitRunner
import org.scalacheck._
import play.api.libs.json._
import play.api.libs.json.Json._
import java.util.UUID

class ModelSpec extends WordSpecLike with Matchers with PropertyChecks with CommonGenerators {

  import models._
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 1, maxSize = 100, minSuccessful = 100, workers = 5)

  "A Model" must {

    "provide class LatLng modelling GPS coordinates" which {
      "should be serializable to/from json array" in {
        forAll(LatGenerator, LngGenerator) { (a: Double, b: Double) =>
          val c = LatLng(a, b)
          c.latitude should be(a)
          c.longitude should be(b)
          toJson(c) should be(JsArray(Seq(JsNumber(a), JsNumber(b))))
          parse(s"[$a,$b]").as[LatLng] should be(c)
          parse(s"""{"0":$a,"1":$b}""").as[LatLng] should be(c)
        }
      }
    }

    "provide class Position modelling GPS position at time" which {
      "should be serializable to/from json object" in {
        forAll(LatLngGenerator, TimeGenerator) { (c: LatLng, t: Long) =>
          val p = Position(c, t)
          p.time should be(t)
          p.location should be(c)
          toJson(p) should be(JsObject(Map("location" -> toJson(c), "time" -> JsNumber(t))))
          parse(s"""{"time": $t, "location": ${stringify(toJson(c))}}""").as[Position] should be(p)
        }
      }
    }

    "provide class Vessel modelling ship at the sea" which {
      "should be serializable to/from json object when id is undefined" in {
        forAll(NameGenerator, DoubleGenerator, DoubleGenerator, DoubleGenerator, PositionGenerator) {
          (n: String, w: Double, l: Double, d: Double, p: Option[Position]) =>
            val v = Vessel(None, n, w, l, d, p)
            v.uuid shouldBe None
            v.name should be(n)
            v.width should be(w)
            v.length should be(l)
            v.draft should be(d)
            v.lastSeenPosition should be(p)
            val json = Json.obj(
              "name" -> JsString(n), "width" -> JsNumber(w), "length" -> JsNumber(l), "draft" -> JsNumber(d)
            )
            toJson(v) should be(p.fold(json)(p => json + ("lastSeenPosition" -> toJson(p))))
            parse(s"""{"width":$w, "length":$l, "name":"$n", """ +
              p.fold("")(p => s""""lastSeenPosition":${stringify(toJson(p))}, """) +
              s""""draft":$d}""").as[Vessel] should be(v)
        }
      }
      "should be serializable to/from json object when id is defined" in {
        forAll(NameGenerator, DoubleGenerator, DoubleGenerator, DoubleGenerator, PositionGenerator) {
          (n: String, w: Double, l: Double, d: Double, p: Option[Position]) =>
            val id = UUID.randomUUID()
            val v = Vessel(Some(id), n, w, l, d, p)
            v.uuid shouldBe Some(id)
            v.name should be(n)
            v.width should be(w)
            v.length should be(l)
            v.draft should be(d)
            v.lastSeenPosition should be(p)
            val json = Json.obj(
              "uuid" -> id, "name" -> JsString(n), "width" -> JsNumber(w), "length" -> JsNumber(l), "draft" -> JsNumber(d))
            toJson(v) should be(p.fold(json)(p => json + ("lastSeenPosition" -> toJson(p))))
            parse(s"""{"width":$w, "length":$l, "uuid":"$id", "name":"$n", """ +
              p.fold("")(p => s""""lastSeenPosition":${stringify(toJson(p))}, """) +
              s""""draft":$d}""").as[Vessel] should be(v)
        }
      }
    }
  }
}