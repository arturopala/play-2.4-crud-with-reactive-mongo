package utils

import org.scalacheck._
import play.api.libs.json._
import play.api.libs.json.Json._

trait JsonGenerators {

  val toJsValue: Any => JsValue = {
    case s: String => JsString(s)
    case f: Float => JsNumber(f)
    case d: Double => JsNumber(d)
    case i: Int => JsNumber(i)
    case l: Long => JsNumber(l)
    case b: Boolean => JsBoolean(b)
    case map: Map[_, _] => JsObject(map.toSeq map { case (k, v) => (k.toString, toJsValue(v)) })
    case seq: Seq[_] => JsArray(seq map toJsValue)
    case _ => JsNull
  }

  val string = Gen.alphaStr
  val integer = Gen.chooseNum[Int](Int.MinValue, Int.MaxValue)
  val float = Gen.chooseNum[Float](Float.MinValue, Float.MaxValue)
  val boolean = Gen.oneOf(true, false)
  val primitives = Gen.oneOf(string, integer, float, boolean)
  val arrays = Gen.oneOf(Gen.listOf(string), Gen.listOf(integer), Gen.listOf(float), Gen.listOf(boolean))
  val name: Gen[String] = string map ("f" + _)
  val objects: Gen[Map[String, _]] = Gen.mapOf(Gen.zip(name, Gen.frequency((6, primitives), (1, arrays))))
  val anyValue: Gen[Any] = Gen.frequency((3, primitives), (1, arrays), (2, objects))

  val jsValue: Gen[JsValue] = anyValue map toJsValue
  val jsField: Gen[(String, JsValue)] = Gen.zip(name, jsValue)
  val simpleJsonObject: Gen[JsObject] = jsField map { case (n, v) => Json.obj(n -> v) }
  val complexJsonObject: Gen[JsObject] = Gen.listOf(jsField) map JsObject.apply

}