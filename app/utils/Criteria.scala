package utils

import scala.util.{ Try, Success, Failure }
import play.api.libs.json._

trait Criteria {

  def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean
  final def matches(document: JsObject): Boolean = matches(document, None)
  def toJson: JsObject

  def &(other: Criteria): Criteria = Criteria.ImplicitAnd(this, other)
  def |(other: Criteria): Criteria = Criteria.Or(this, other)

}

object Criteria extends CriteriaImplicits {

  import CriteriaUtils._

  def apply(json: JsObject): Try[Criteria] = Try {
    parseObjectAsCriteria(json)
  }

  val parseObjectAsCriteria: JsObject => Criteria = {
    case IsAnd(seq) =>
      And((seq map parseObjectAsCriteria): _*)
    case IsOr(seq) =>
      Or((seq map parseObjectAsCriteria): _*)
    case HasSingleField(name, value) =>
      parseFieldAsCriteria((name, value))
    case HasMultipleFields(seq) =>
      seq map parseFieldAsCriteria reduceOrElse (ImplicitAnd.apply, Always)
    case IsEmpty(_) => Always
  }

  val parseFieldAsCriteria: ((String, JsValue)) => Criteria = {
    case (path, IsOperator(obj)) => Op(path, parseValueAsCriteria(path)(obj))
    case (path, value) => Eq(path, value)
  }

  val parseValueAsCriteria: String => JsObject => Criteria =
    path => {
      case RegexOperator(pattern, options) => Regex(pattern, options)
      case NotOperator(obj) => Not(parseValueAsCriteria(path)(obj))
      case EqOperator(value) => ExplicitEq(value)
      case obj => Eq(path, obj)
    }

  object Always extends Criteria {
    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = true
    override def toJson = Json.obj()
  }

  case class Eq(path: String, pattern: JsValue) extends FieldValueCriteria {
    override def matchValue(value: JsValue): Boolean = matchJsValues(pattern, value)
    override def toJson = Json.obj(path -> pattern)
  }

  object Eq {
    def apply(path: String, value: String): Eq = new Eq(path, JsString(value))
    def apply(path: String, value: Int): Eq = new Eq(path, JsNumber(value))
    def apply(path: String, value: Double): Eq = new Eq(path, JsNumber(value))
    def apply(path: String, value: Boolean): Eq = new Eq(path, JsBoolean(value))
  }

  case class ExplicitEq(pattern: JsValue) extends FieldValueCriteriaOperator {
    override def matchValue(value: JsValue): Boolean = matchJsValues(pattern, value)
    override def toJson = Json.obj("$eq" -> pattern)
  }

  case class Op(path: String, criteria: Criteria) extends PathCriteria {

    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = criteria.matches(document, Some(jsPath))
    override def toJson = Json.obj(path -> criteria.toJson)
  }

  case class And(cs: Criteria*) extends Criteria {
    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = cs forall (_.matches(document, pathOpt))
    override def toJson = Json.obj("$and" -> JsArray(cs map (_.toJson)))
  }

  case class ImplicitAnd(c1: Criteria, c2: Criteria) extends Criteria {
    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = c1.matches(document, pathOpt) && c2.matches(document, pathOpt)
    override def toJson = c1.toJson ++ c2.toJson
  }

  case class Or(cs: Criteria*) extends Criteria {
    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = cs forall (_.matches(document, pathOpt))
    override def toJson = Json.obj("$or" -> JsArray(cs map (_.toJson)))
  }

  case class Not(c: Criteria) extends Criteria {
    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = !c.matches(document, pathOpt)
    override def toJson = Json.obj("$not" -> c.toJson)
  }

  case class Regex(pattern: String, options: Option[String] = None) extends FieldValueCriteriaOperator {
    import java.util.regex.Pattern

    def i(o: String): Int = if (o.indexOf("i") >= 0) Pattern.CASE_INSENSITIVE else 0
    def m(o: String): Int = if (o.indexOf("m") >= 0) Pattern.MULTILINE else 0
    def x(o: String): Int = if (o.indexOf("x") >= 0) Pattern.COMMENTS else 0
    def s(o: String): Int = if (o.indexOf("s") >= 0) Pattern.DOTALL else 0

    val flags = options.map(o => i(o) | m(o) | s(o) | x(o)).getOrElse(0)
    val regex = Pattern.compile(pattern, flags)

    override def matchValue(value: JsValue): Boolean = value match {
      case JsString(s) => regex.matcher(s).matches
      case _ => false
    }

    override def toJson = {
      val json = Json.obj("$regex" -> JsString(pattern))
      options match {
        case Some(o) => json + ("$options" -> JsString(o))
        case None => json
      }
    }
  }

}

trait CriteriaImplicits {

  implicit class TryAsCriteria(t: Try[Criteria]) extends Criteria {
    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = t match {
      case Success(c) => c.matches(document, pathOpt)
      case Failure(e) => false
    }
    override def toJson = t match {
      case Success(c) => c.toJson
      case Failure(e) => throw e
    }
  }

  implicit class SeqTryReduce[A](seq: Seq[A]) {
    def reduceOrElse(f: (A, A) => A, a: A): A =
      if (seq.isEmpty) a else seq reduce f
  }

}

object CriteriaUtils {

  trait PathCriteria extends Criteria {
    val path: String
    lazy val jsPath: JsPath = path.split('.').foldLeft(JsPath())((a, p) => a \ p)
  }

  trait FieldValueCriteria extends PathCriteria {
    def matchValue(value: JsValue): Boolean

    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = jsPath.asSingleJson(document) match {
      case JsDefined(value) => matchValue(value)
      case _ => false
    }
  }

  trait FieldValueCriteriaOperator extends Criteria {
    def matchValue(value: JsValue): Boolean

    override def matches(document: JsObject, pathOpt: Option[JsPath]): Boolean = pathOpt match {
      case Some(jsPath) => jsPath.asSingleJson(document) match {
        case JsDefined(value) => matchValue(value)
        case _ => false
      }
      case None => false
    }

  }

  val matchJsValues: PartialFunction[(JsValue, JsValue), Boolean] = {
    case (JsNull, JsNull) => true
    case (JsString(s1), JsString(s2)) => s1 == s2
    case (JsBoolean(b1), JsBoolean(b2)) => b1 == b2
    case (JsNumber(n1), JsNumber(n2)) => n1 == n2
    //Equality matches on the array require that the array field match exactly, including the element order.
    case (JsArray(seq1), JsArray(seq2)) => seq1.zip(seq2).forall {
      case (c, i) => matchJsValues(c, i)
    }
    //Equality matches on an embedded document require an exact match, including the field order
    case (c: JsObject, d: JsObject) => c.fields.sameElements(d.fields)
    case _ => false
  }

  trait OperatorExtractor[A] {
    val key: String
    def extract(value: JsValue, obj: JsObject): A

    final def unapply(obj: JsObject): Option[A] =
      obj.value.get(key).map(value => extract(value, obj))
  }

  object HasMultipleFields {
    final def unapply(obj: JsObject): Option[Seq[(String, JsValue)]] =
      if (obj.value.size > 1) Option(obj.fields) else None
  }

  object HasSingleField {
    final def unapply(obj: JsObject): Option[(String, JsValue)] =
      if (obj.value.size == 1) obj.value.headOption else None
  }

  object IsEmpty {
    final def unapply(obj: JsObject): Option[Boolean] =
      if (obj.value.size == 0) Some(true) else None
  }

  object IsOperator {
    final def unapply(value: JsValue): Option[JsObject] =
      value match {
        case obj: JsObject =>
          if (obj.keys.exists(k => k.startsWith("$"))) Some(obj) else None
        case _ => None
      }
  }

  object JsArrayOfObjects {
    val isJsObject: JsValue => Boolean = {
      case JsObject(_) => true
      case _ => false
    }
    def unapply(jsValue: JsValue): Option[Seq[JsObject]] = jsValue match {
      case JsArray(seq) if seq forall isJsObject => Some(seq map (_.as[JsObject]))
      case _ => None
    }
  }

  object IsAnd extends OperatorExtractor[Seq[JsObject]] {
    val key = "$and"
    override def extract(value: JsValue, obj: JsObject) =
      value match {
        case JsArrayOfObjects(seq) => seq
        case _ => throw new IllegalArgumentException("$and query operator must be an array-of-objects")
      }
  }

  object IsOr extends OperatorExtractor[Seq[JsObject]] {
    val key = "$or"
    override def extract(value: JsValue, obj: JsObject) =
      value match {
        case JsArrayOfObjects(seq) => seq
        case _ => throw new IllegalArgumentException("$or query operator must be an array-of-objects")
      }
  }

  object RegexOperator extends OperatorExtractor[(String, Option[String])] {
    val key = "$regex"
    override def extract(value: JsValue, obj: JsObject): (String, Option[String]) = {
      val pattern = value.as[String]
      val optionsOpt = Option(obj).flatMap(o => o.value.get("$options").map(_.as[String]))
      (pattern, optionsOpt)
    }
  }

  object NotOperator extends OperatorExtractor[JsObject] {
    val key = "$not"
    override def extract(value: JsValue, obj: JsObject): JsObject = {
      value.as[JsObject]
    }
  }

  object EqOperator extends OperatorExtractor[JsValue] {
    val key = "$eq"
    override def extract(value: JsValue, obj: JsObject): JsValue = {
      value
    }
  }

}