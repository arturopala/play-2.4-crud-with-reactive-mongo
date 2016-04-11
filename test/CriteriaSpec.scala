package utils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success, Try }

import org.scalacheck._
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.PropertyChecks
import play.api.libs.json._
import play.api.libs.json.Json._
import utils._

class CriteriaSpec extends WordSpecLike with Matchers with PropertyChecks with JsonGenerators with ScalaFutures {

  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSize = 0, maxSize = 10, minSuccessful = 100, workers = 1)

  import Criteria._

  "A Criteria" must {

    "parse empty json object as Always criteria" in {
      val query = Json.obj()
      Criteria(query) shouldBe Success(Criteria.Always)
    }

    "parse simple json object as Eq criteria" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> value)
        Criteria(query) shouldBe Success(Criteria.Eq(name, value))
      }
    }

    "parse complex json object as an Eq or ImplicitAnd criteria" in {
      forAll(complexJsonObject) { (json: JsObject) =>
        whenever(json.fields.size > 1) {
          (Criteria(json) match {
            case Success(Criteria.ImplicitAnd(_, _)) => true
            case x => false
          }) shouldBe true
        }
      }
    }

    "parse $regex query operator as a Regex criteria when valid expression" in {
      forAll(name) { (name: String) =>
        val regex = "^foo.*"
        val query = Json.obj(name -> Json.obj("$regex" -> regex))
        Criteria(query) shouldBe Success(Op(name, Regex(regex, None)))
      }
    }

    "parse $regex with $options query operator as a Regex criteria when valid expression" in {
      forAll(name) { (name: String) =>
        val regex = "^foo.*"
        val query = Json.obj(name -> Json.obj("$regex" -> regex, "$options" -> name))
        Criteria(query) shouldBe Success(Op(name, Regex(regex, Some(name))))
      }
    }

    "not parse $regex query operator as a Regex criteria when invalid expression" in {
      forAll(name) { (name: String) =>
        val regex = "***"
        val query = Json.obj(name -> Json.obj("$regex" -> regex))
        Criteria(query) should matchPattern { case Failure(_) => }
      }
    }

    "parse $or query operator as an Or criteria when value is an array of sole objects" in {
      forAll(name, simpleJsonObject, complexJsonObject, simpleJsonObject) {
        (name: String, obj1: JsObject, obj2: JsObject, obj3: JsObject) =>
          val query = Json.obj("$or" -> JsArray(Seq(Json.obj(name -> obj1), Json.obj(name -> obj2), Json.obj(name -> obj3))))
          Criteria(query) shouldBe Success(Criteria.Or(Criteria.Eq(name, obj1), Criteria.Eq(name, obj2), Criteria.Eq(name, obj3)))
      }
    }

    "not parse $or query operator as an Or criteria when value is an array but not entire objects" in {
      forAll(name) { (name: String) =>
        val query = Json.obj("$or" -> Json.arr(Json.obj(name -> JsString("foo")), JsString("bar")))
        Criteria(query) should matchPattern { case Failure(_) => }
      }
    }

    "not parse $or query operator as an Or criteria when value is not an array" in {
      forAll(name) { (name: String) =>
        val query = Json.obj("$or" -> Json.obj(name -> JsString(name)))
        Criteria(query) should matchPattern { case Failure(_) => }
      }
    }

    "parse $and query operator as an And criteria when value is an array of sole objects" in {
      forAll(name, simpleJsonObject, complexJsonObject, simpleJsonObject) {
        (name: String, obj1: JsObject, obj2: JsObject, obj3: JsObject) =>
          val query = Json.obj("$and" -> JsArray(Seq(Json.obj(name -> obj1), Json.obj(name -> obj2), Json.obj(name -> obj3))))
          Criteria(query) shouldBe Success(Criteria.And(Criteria.Eq(name, obj1), Criteria.Eq(name, obj2), Criteria.Eq(name, obj3)))
      }
    }

    "not parse $and query operator as an And criteria when value is array but not only objects" in {
      forAll(name) { (name: String) =>
        val query = Json.obj("$and" -> Json.arr(Json.obj(name -> JsString("foo")), JsString("bar")))
        Criteria(query) should matchPattern { case Failure(_) => }
      }
    }

    "not parse $and query operator as an And criteria when value is not an array" in {
      forAll(name) { (name: String) =>
        val query = Json.obj("$and" -> Json.obj(name -> JsString(name)))
        Criteria(query) should matchPattern { case Failure(_) => }
      }
    }
  }

  "An Eq criteria" must {

    "match equality between simple json document parsed as a criteria and itself" in {
      forAll(simpleJsonObject) { (json: JsObject) =>
        Criteria(json).matches(json) shouldBe true
      }
    }

    "match equality between complex json document parsed as a criteria and itself" in {
      forAll(complexJsonObject) { (json: JsObject) =>
        whenever(json.fields.size > 0) {
          Criteria(json).matches(json) shouldBe true
        }
      }
    }

    "match equality when query consists of a single field present in the document" in {
      forAll(complexJsonObject, name, jsValue) { (json: JsObject, name: String, value: JsValue) =>
        val query = Json.obj(name -> value)
        val document = json ++ query
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match equality when query consists of a single field not present in the document" in {
      forAll(complexJsonObject, name, jsValue) { (json: JsObject, name: String, value: JsValue) =>
        val query = Json.obj(("invalid_" + name) -> value)
        Criteria(query).matches(json) shouldBe false
      }
    }

    "match equality when query is a subset of the document" in {
      forAll(complexJsonObject, complexJsonObject) { (query: JsObject, base: JsObject) =>
        whenever(base.fields.size > 0 && query.fields.size > 0) {
          val document = base ++ query
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "not match equality when query is not a subset of the document" in {
      forAll(complexJsonObject, complexJsonObject) { (query: JsObject, base: JsObject) =>
        whenever(base.fields.size > 0 && query.fields.size > 0) {
          val document = base ++ query
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "serialize back to the json format" in {
      forAll(complexJsonObject) { (json: JsObject) =>
        whenever(json.fields.size > 0) {
          Criteria(json).toJson shouldBe json
        }
      }
    }
  }

  "An And criteria" must {

    "match when both criteria are met" in {
      forAll(simpleJsonObject, simpleJsonObject) { (obj1: JsObject, obj2: JsObject) =>
        whenever(obj1 != obj2 && obj1.keys != obj2.keys) {
          val document = obj1 ++ obj2
          val query = Json.obj("$and" -> Json.arr(obj2, obj1))
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "not match when only single criteria is met" in {
      forAll(simpleJsonObject, simpleJsonObject) { (obj1: JsObject, obj2: JsObject) =>
        whenever(obj1 != obj2 && obj1.keys != obj2.keys) {
          val document = obj1
          val query = Json.obj("$and" -> Json.arr(obj2, obj1))
          Criteria(query).matches(document) shouldBe false
        }
      }
    }

    "not match when not all criteria are met" in {
      forAll(simpleJsonObject, simpleJsonObject, simpleJsonObject) { (obj1: JsObject, obj2: JsObject, obj3: JsObject) =>
        whenever(obj1.keys != obj2.keys && obj1.keys != obj3.keys && obj2.keys != obj3.keys) {
          val document = obj1 ++ obj3
          val query = Json.obj("$and" -> Json.arr(obj2, obj1, obj3))
          Criteria(query).matches(document) shouldBe false
        }
      }
    }
  }

  "An Or criteria" must {

    "match when one of criteria is met" in {
      forAll(simpleJsonObject, simpleJsonObject) { (obj1: JsObject, obj2: JsObject) =>
        whenever(obj1 != obj2 && obj1.keys != obj2.keys) {
          val document = obj1 ++ obj2
          val query1 = Json.obj("$or" -> Json.arr(obj2))
          Criteria(query1).matches(document) shouldBe true
          val query2 = Json.obj("$or" -> Json.arr(obj1))
          Criteria(query2).matches(document) shouldBe true
        }
      }
    }

    "not match when none criteria are met" in {
      forAll(simpleJsonObject, simpleJsonObject, simpleJsonObject) { (obj1: JsObject, obj2: JsObject, obj3: JsObject) =>
        whenever(obj1.keys != obj2.keys && obj1.keys != obj3.keys && obj2.keys != obj3.keys) {
          val query1 = Json.obj("$and" -> Json.arr(obj2))
          Criteria(query1).matches(obj1 ++ obj3) shouldBe false
          val query2 = Json.obj("$and" -> Json.arr(obj3))
          Criteria(query2).matches(obj1 ++ obj2) shouldBe false
          val query3 = Json.obj("$and" -> Json.arr(obj1))
          Criteria(query3).matches(obj2 ++ obj3) shouldBe false
        }
      }
    }
  }

  "An Regex criteria" must {

    "match string value by prefix" in {
      forAll(name, string) { (name: String, value: String) =>
        whenever(value.length > 0) {
          val query = Json.obj(name -> Json.obj("$regex" -> s"^${value.head}.*"))
          val document = Json.obj(name -> JsString(value))
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "match string value by catch-all expression" in {
      forAll(name, string) { (name: String, value: String) =>
        whenever(value.length > 0) {
          val query = Json.obj(name -> Json.obj("$regex" -> "^.*"))
          val document = Json.obj(name -> JsString(value))
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "match string value by suffix" in {
      forAll(name, string) { (name: String, value: String) =>
        whenever(value.length > 0) {
          val query = Json.obj(name -> Json.obj("$regex" -> s"^.*?${value.last}"))
          val document = Json.obj(name -> JsString(value))
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "match string value ignoring case" in {
      forAll(name, string) { (name: String, value: String) =>
        whenever(value.length > 0) {
          val query = Json.obj(name -> Json.obj("$regex" -> s"^${value.head.toUpper}.*", "$options" -> "i"))
          val document = Json.obj(name -> JsString(value.toLowerCase))
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "not match string value by some regular expression" in {
      forAll(name, string) { (name: String, value: String) =>
        val query = Json.obj(name -> Json.obj("$regex" -> "_s"))
        val document = Json.obj(name -> JsString(value))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }

  "An Not criteria" must {

    "match when field does not exist" in {
      val document = Json.obj("foo" -> "bar")
      val query = Json.obj("fo" -> Json.obj("$not" -> Json.obj("$eq" -> "baar")))
      Criteria(query).matches(document) shouldBe true
    }

    "match when field does not exist (2)" in {
      forAll(jsField) { (field: (String, JsValue)) =>
        val (name, value) = field
        val document = Json.obj(name -> value)
        val query = Json.obj(("_" + name) -> Json.obj("$not" -> Json.obj("$eq" -> value)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match when field exists but inner criteria not met" in {
      forAll(jsField) { (field: (String, JsValue)) =>
        val (name, value) = field
        val document = Json.obj(name -> value)
        val query = Json.obj(name -> Json.obj("$not" -> Json.obj("$eq" -> "foobar")))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match when field exists but inner criteria met" in {
      forAll(jsField) { (field: (String, JsValue)) =>
        val (name, value) = field
        val document = Json.obj(name -> value)
        val query = Json.obj(name -> Json.obj("$not" -> Json.obj("$eq" -> value)))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }
}
