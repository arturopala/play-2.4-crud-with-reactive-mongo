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

    "parse empty json object as an Always criteria" in {
      val query = Json.obj()
      Criteria(query) shouldBe Success(Always)
    }

    "parse simple json object as an Eq criteria" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> value)
        Criteria(query) shouldBe Success(Eq(name, value))
      }
    }

    "parse complex json object as an ImplicitAnd criteria" in {
      forAll(complexJsonObject) { (json: JsObject) =>
        whenever(json.fields.size > 1) {
          (Criteria(json) match {
            case Success(ImplicitAnd(_, _)) => true
            case x => false
          }) shouldBe true
        }
      }
    }

    "parse complex json query expression as a criteria" in {
      val query = Json.parse("""{"$and":[{"a":"foo"},{"b":{"c":15.189}},{"$or":[{"d":true},{"d":{"$gt":66316263}}]}]}""")
      Criteria(query.as[JsObject]) shouldBe Success(
        And(
          Eq("a", "foo"),
          Eq("b", Json.obj("c" -> 15.189)),
          Or(
            Eq("d", true),
            If("d" -> Gt(66316263))
          )
        )
      )
    }

    "parse $regex query operator as a Regex constraint when valid expression" in {
      forAll(name) { (name: String) =>
        val regex = "^foo.*"
        val query = Json.obj(name -> Json.obj("$regex" -> regex))
        Criteria(query) shouldBe Success(If(name, Regex(regex, None)))
      }
    }

    "parse $regex with $options query operator as a Regex constraint when valid expression" in {
      forAll(name) { (name: String) =>
        val regex = "^foo.*"
        val query = Json.obj(name -> Json.obj("$regex" -> regex, "$options" -> name))
        Criteria(query) shouldBe Success(If(name, Regex(regex, Some(name))))
      }
    }

    "not parse $regex query operator as a Regex constraint when invalid expression" in {
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
          Criteria(query) shouldBe Success(Or(Eq(name, obj1), Eq(name, obj2), Eq(name, obj3)))
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
          Criteria(query) shouldBe Success(And(Eq(name, obj1), Eq(name, obj2), Eq(name, obj3)))
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

    "parse $ne query operator as a Ne constraint" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> Json.obj("$ne" -> value))
        Criteria(query) shouldBe Success(If(name, Ne(value)))
      }
    }

    "parse $lt query operator as a Lt constraint" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> Json.obj("$lt" -> value))
        Criteria(query) shouldBe Success(If(name, Lt(value)))
      }
    }

    "parse $gt query operator as a Gt constraint" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> Json.obj("$gt" -> value))
        Criteria(query) shouldBe Success(If(name, Gt(value)))
      }
    }

    "parse $lte query operator as a Lte constraint" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> Json.obj("$lte" -> value))
        Criteria(query) shouldBe Success(If(name, Lte(value)))
      }
    }

    "parse $gte query operator as a Gte constraint" in {
      forAll(name, jsValue) { (name: String, value: JsValue) =>
        val query = Json.obj(name -> Json.obj("$gte" -> value))
        Criteria(query) shouldBe Success(If(name, Gte(value)))
      }
    }
  }

  "An Eq (implicit equality) criteria" must {

    "match equality between simple json document parsed as an equality criteria and itself" in {
      forAll(simpleJsonObject) { (json: JsObject) =>
        Criteria(json).matches(json) shouldBe true
      }
    }

    "match equality between complex json document parsed as a set of implicit equality criteria and itself" in {
      forAll(complexJsonObject) { (json: JsObject) =>
        whenever(json.fields.size > 0) {
          Criteria(json).matches(json) shouldBe true
        }
      }
    }

    "match equality when field present in the flat document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        val document = json ++ Json.obj(name -> value)
        val query = Json.obj(name -> value)
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match when field present in the deeply nested document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        whenever(!name.isEmpty()) {
          val document = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> (json ++ Json.obj(name -> value)))))
          val query = Json.obj(s"a.b.c.$name" -> value)
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "not match when field not found in the flat document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        val document = json ++ Json.obj(name -> value)
        val query = Json.obj(s"_$name" -> value)
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field not found in the deeply nested document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        whenever(!name.isEmpty()) {
          val document = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> (json ++ Json.obj(name -> value)))))
          val query = Json.obj(s"b.c.$name" -> value)
          Criteria(query).matches(document) shouldBe false
        }
      }
    }

    "match equality when query consists of a single field already present in the document" in {
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

    "match equality when query expression is a subset of the document" in {
      forAll(complexJsonObject, complexJsonObject) { (query: JsObject, base: JsObject) =>
        whenever(base.fields.size > 0 && query.fields.size > 0) {
          val document = base ++ query
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "not match equality when query expression is not a subset of the document" in {
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

    "match equality when document field value is an array and constraint pattern is an exact array" in {
      forAll(complexJsonObject, name, jsArray) { (json: JsObject, name: String, array: JsArray) =>
        val query = Json.obj(name -> array)
        val document = json ++ query
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match equality when document field value is an array and constraint pattern is an extented array" in {
      forAll(complexJsonObject, name, jsArray) { (json: JsObject, name: String, array: JsArray) =>
        val query = Json.obj(name -> (array :+ JsString("foo")))
        val document = json ++ Json.obj(name -> array)
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match equality when document field value is an array and constraint pattern is a shrinked array" in {
      forAll(complexJsonObject, name, jsArray) { (json: JsObject, name: String, array: JsArray) =>
        val query = Json.obj(name -> array)
        val document = json ++ Json.obj(name -> (array :+ JsString("foo")))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match equality when document field value is an array and constraint pattern is a simillar but reordered array" in {
      forAll(complexJsonObject, name, jsArray) { (json: JsObject, name: String, array: JsArray) =>
        whenever(array.value.length > 0) {
          val query = Json.obj(name -> (JsString("foo") +: array))
          val document = json ++ Json.obj(name -> (array :+ JsString("foo")))
          Criteria(query).matches(document) shouldBe false
        }
      }
    }

    "match equality when document field value is an array and constraint pattern is an element of that array" in {
      forAll(complexJsonObject, name, jsArray, jsValue) { (json: JsObject, name: String, array: JsArray, element: JsValue) =>
        val query = Json.obj(name -> element)
        val document = json ++ Json.obj(name -> (array :+ element))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match equality when document field value is an array and constraint pattern is an array element of that array" in {
      forAll(complexJsonObject, name, jsArray, jsArray) { (json: JsObject, name: String, array: JsArray, element: JsArray) =>
        val query = Json.obj(name -> element)
        val document = json ++ Json.obj(name -> (array :+ element))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match equality when document field value is an object and constraint pattern is an exact object" in {
      forAll(complexJsonObject, name, simpleJsonObject) { (json: JsObject, name: String, obj: JsObject) =>
        val query = Json.obj(name -> obj)
        val document = json ++ query
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match equality when document field value is an object and constraint pattern is an extended object" in {
      forAll(complexJsonObject, name, simpleJsonObject, jsField) { (json: JsObject, name: String, obj: JsObject, field: (String, JsValue)) =>
        val query = Json.obj(name -> (json ++ (obj + field)))
        val document = Json.obj(name -> json)
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match equality when document field value is an object and constraint pattern is a shrinked object" in {
      forAll(complexJsonObject, name, simpleJsonObject, jsField) { (json: JsObject, name: String, obj: JsObject, field: (String, JsValue)) =>
        val query = Json.obj(name -> json)
        val document = Json.obj(name -> (json ++ (obj + field)))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }

  "An EEq (explicit equality) constraint" must {

    "match when field value matches in the flat document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        val document = json ++ Json.obj(name -> value)
        val query = Json.obj(name -> Json.obj("$eq" -> value))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match when field value matches in the deeply nested document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        whenever(!name.isEmpty()) {
          val document = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> (json ++ Json.obj(name -> value)))))
          val query = Json.obj(s"a.b.c.$name" -> Json.obj("$eq" -> value))
          Criteria(query).matches(document) shouldBe true
        }
      }
    }

    "not match when field not found in the flat document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        val document = json ++ Json.obj(name -> value)
        val query = Json.obj(s"_$name" -> Json.obj("$eq" -> value))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field not found in the deeply nested document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        whenever(!name.isEmpty()) {
          val document = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> (json ++ Json.obj(name -> value)))))
          val query = Json.obj(s"a.b.$name" -> Json.obj("$eq" -> value))
          Criteria(query).matches(document) shouldBe false
        }
      }
    }

    "not match when field has different value" in {
      forAll(name, complexJsonObject) { (name: String, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsString("foo"))
        val query = Json.obj(name -> Json.obj("$eq" -> JsString("bar")))
        Criteria(query).matches(document) shouldBe false
      }
    }

  }

  "A Ne constraint" must {

    "match when field has different value" in {
      forAll(name, complexJsonObject) { (name: String, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsString("foo"))
        val query = Json.obj(name -> Json.obj("$ne" -> JsString("bar")))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match when field value matches in the flat document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        val document = json ++ Json.obj(name -> value)
        val query = Json.obj(name -> Json.obj("$ne" -> value))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field value matches in the deeply nested document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        whenever(!name.isEmpty()) {
          val document = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> (json ++ Json.obj(name -> value)))))
          val query = Json.obj(s"a.b.c.$name" -> Json.obj("$ne" -> value))
          Criteria(query).matches(document) shouldBe false
        }
      }
    }

    "not match when field not found in the flat document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        val document = json ++ Json.obj(name -> value)
        val query = Json.obj(s"_$name" -> Json.obj("$ne" -> value))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field not found in the deeply nested document" in {
      forAll(name, jsValue, complexJsonObject) { (name: String, value: JsValue, json: JsObject) =>
        whenever(!name.isEmpty()) {
          val document = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> (json ++ Json.obj(name -> value)))))
          val query = Json.obj(s"a.b.$name" -> Json.obj("$ne" -> value))
          Criteria(query).matches(document) shouldBe false
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
          val query1 = Json.obj("$or" -> Json.arr(obj2))
          Criteria(query1).matches(obj1 ++ obj3) shouldBe false
          val query2 = Json.obj("$or" -> Json.arr(obj3))
          Criteria(query2).matches(obj1 ++ obj2) shouldBe false
          val query3 = Json.obj("$or" -> Json.arr(obj1))
          Criteria(query3).matches(obj2 ++ obj3) shouldBe false
        }
      }
    }

    "not match when document is empty" in {
      forAll(simpleJsonObject) { (obj1: JsObject) =>
        val query = Json.obj("$or" -> Json.arr(obj1))
        Criteria(query).matches(Json.obj()) shouldBe false
      }
    }
  }

  "A Regex constraint" must {

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

  "A Not constraint" must {

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

  "A Lt constraint" must {

    "match when field value is less then constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(name -> Json.obj("$lt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match when field value is equal to constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number))
        val query = Json.obj(name -> Json.obj("$lt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field value is greater than constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number + 1))
        val query = Json.obj(name -> Json.obj("$lt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field not found in the document" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(s"_$name" -> Json.obj("$lt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }

  "A Gt constraint" must {

    "not match when field value is less then constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(name -> Json.obj("$gt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field value is equal to constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number))
        val query = Json.obj(name -> Json.obj("$gt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "match when field value is greater than constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number + 1))
        val query = Json.obj(name -> Json.obj("$gt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match when field not found in the document" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(s"_$name" -> Json.obj("$gt" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }

  "A Lte constraint" must {

    "match when field value is less then constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(name -> Json.obj("$lte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match when field value is equal to constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number))
        val query = Json.obj(name -> Json.obj("$lte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match when field value is greater than constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number + 1))
        val query = Json.obj(name -> Json.obj("$lte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "not match when field not found in the document" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(s"_$name" -> Json.obj("$lte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }

  "A Gte constraint" must {

    "not match when field value is less then constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(name -> Json.obj("$gte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }

    "match when field value is equal to constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number))
        val query = Json.obj(name -> Json.obj("$gte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "match when field value is greater than constraint" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number + 1))
        val query = Json.obj(name -> Json.obj("$gte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe true
      }
    }

    "not match when field not found in the document" in {
      forAll(name, integer, complexJsonObject) { (name: String, number: Int, json: JsObject) =>
        val document = json ++ Json.obj(name -> JsNumber(number - 1))
        val query = Json.obj(s"_$name" -> Json.obj("$gte" -> JsNumber(number)))
        Criteria(query).matches(document) shouldBe false
      }
    }
  }
}
