package utils

import scala.util.{ Try, Success, Failure }
import play.api.libs.json._

/**
 * Criteria is a top-level expression used to query/filter documents in MongoDB.
 * Can be used to parse and evaluate existing JSON queries
 * or to construct them type-safe way.
 */
trait Criteria {
  /**Checks if given document fullfils this criteria*/
  def matches(document: JsObject): Boolean
  def missingValueMatchResult: Boolean = false
  /**Serializes criteria back to JSON query expression*/
  def toJson: JsObject

  def &&(other: Criteria): Criteria = Criteria.And(this, other)
  def ||(other: Criteria): Criteria = Criteria.Or(this, other)
}

/**
 * Constraint is an expression's operator used to constraint .
 */
trait Constraint {
  /**Checks if document matches this constraint at given path*/
  def matches(value: JsValue): Boolean
  def missingValueMatchResult: Boolean = false
  /**Serializes constraint back to JSON query expression*/
  def toJson: JsObject
}

/**
 * Supported criteria and constraints set.
 */
object Criteria extends CriteriaImplicits {

  import CriteriaUtils._

  /**Parses JSON query expression as a Criteria*/
  def apply(json: JsObject): Try[Criteria] = Try {
    parseObjectAsCriteria(json)
  }

  private val parseObjectAsCriteria: JsObject => Criteria = {
    case AndConstraint(seq) =>
      And((seq map parseObjectAsCriteria): _*)
    case OrConstraint(seq) =>
      Or((seq map parseObjectAsCriteria): _*)
    case HasSingleField(name, value) =>
      parseFieldAsCriteria((name, value))
    case HasMultipleFields(seq) =>
      seq map parseFieldAsCriteria reduceOrElse (ImplicitAnd.apply, Always)
    case IsEmpty(_) => Always
  }

  private val parseFieldAsCriteria: ((String, JsValue)) => Criteria = {
    case (path, IsConstraint(obj)) => If(path, parseConstraint(path)(obj))
    case (path, value) => Eq(path, value)
  }

  private val parseConstraint: String => JsObject => Constraint =
    path => {
      //Comparison operators
      case LtConstraint(value) => Lt(value)
      case GtConstraint(value) => Gt(value)
      case LteConstraint(value) => Lte(value)
      case GteConstraint(value) => Gte(value)
      case EqConstraint(value) => EEq(value)
      case NeConstraint(value) => Ne(value)
      //Evaluation operators
      case RegexConstraint(pattern, options) => Regex(pattern, options)
      //Logical operators
      case NotConstraint(obj) => Not(parseConstraint(path)(obj))
      //Otherwise must be equal
      case obj => EEq(obj)
    }

  /**Always matching criteria*/
  object Always extends Criteria {
    override def matches(document: JsObject): Boolean = true
    override def toJson = Json.obj()
  }

  /**Implicit equality criteria*/
  case class Eq(path: String, pattern: JsValue) extends PathValueCriteria {
    override def matchValue(value: JsValue): Boolean = JsValueMatch.matches(value, pattern)
    override def toJson = Json.obj(path -> pattern)
  }

  object Eq {
    def apply(path: String, value: String): Eq = new Eq(path, JsString(value))
    def apply(path: String, value: Int): Eq = new Eq(path, JsNumber(value))
    def apply(path: String, value: Double): Eq = new Eq(path, JsNumber(value))
    def apply(path: String, value: Boolean): Eq = new Eq(path, JsBoolean(value))
  }

  /**Wraps Constraint as a Criteria*/
  case class If(path: String, c: Constraint) extends PathValueCriteria {
    override def matchValue(value: JsValue): Boolean = c.matches(value)
    override def toJson = Json.obj(path -> c.toJson)
    override val missingValueMatchResult: Boolean = c.missingValueMatchResult
  }

  object If {
    def apply(t: (String, Constraint)): If = new If(t._1, t._2)
  }

  ////////////////////////////
  //Logical Query Constraints //
  ////////////////////////////

  /**Joins criteria with a logical AND, returns all that match the conditions of both clauses*/
  case class And(cs: Criteria*) extends Criteria {
    override def matches(document: JsObject): Boolean = cs forall (_.matches(document))
    override def toJson = Json.obj(AndConstraint.key -> JsArray(cs map (_.toJson)))
  }

  /**Implicit AND operation*/
  case class ImplicitAnd(c1: Criteria, c2: Criteria) extends Criteria {
    override def matches(document: JsObject): Boolean = c1.matches(document) && c2.matches(document)
    override def toJson = c1.toJson ++ c2.toJson
  }

  /**Joins criteria with a logical OR, returns all that match the conditions of both clauses*/
  case class Or(cs: Criteria*) extends Criteria {
    override def matches(document: JsObject): Boolean = cs exists (_.matches(document))
    override def toJson = Json.obj(OrConstraint.key -> JsArray(cs map (_.toJson)))
  }

  /**Inverts the effect of a nested operator(s)*/
  case class Not(c: Constraint) extends Constraint {
    override def matches(value: JsValue): Boolean = !c.matches(value)
    override def toJson = Json.obj(NotConstraint.key -> c.toJson)
    override val missingValueMatchResult: Boolean = true
  }

  ///////////////////////////////
  //Evaluation Query Constraints //
  ///////////////////////////////

  /**Selects where values match a specified regular expression*/
  case class Regex(pattern: String, options: Option[String] = None) extends Constraint {
    import java.util.regex.Pattern

    def i(o: String): Int = if (o.indexOf("i") >= 0) Pattern.CASE_INSENSITIVE else 0
    def m(o: String): Int = if (o.indexOf("m") >= 0) Pattern.MULTILINE else 0
    def x(o: String): Int = if (o.indexOf("x") >= 0) Pattern.COMMENTS else 0
    def s(o: String): Int = if (o.indexOf("s") >= 0) Pattern.DOTALL else 0

    val flags = options.map(o => i(o) | m(o) | s(o) | x(o)).getOrElse(0)
    val regex = Pattern.compile(pattern, flags)

    override def matches(value: JsValue): Boolean = value match {
      case JsString(s) => regex.matcher(s).matches
      case _ => false
    }

    override def toJson = {
      val json = Json.obj(RegexConstraint.key -> JsString(pattern))
      options match {
        case Some(o) => json + (RegexConstraint.optionsKey -> JsString(o))
        case None => json
      }
    }
  }

  ///////////////////////////////
  //Comparison Query Constraints //
  ///////////////////////////////

  /**Matches values that are equal to a specified value*/
  case class EEq(pattern: JsValue) extends SimpleConstraint(EqConstraint.key) {
    override def matches(value: JsValue): Boolean = JsValueMatch.matches(value, pattern)
  }

  object EEq {
    def apply(value: String): EEq = new EEq(JsString(value))
    def apply(value: Int): EEq = new EEq(JsNumber(value))
    def apply(value: Double): EEq = new EEq(JsNumber(value))
    def apply(value: Boolean): EEq = new EEq(JsBoolean(value))
  }

  /**Matches values that are not equal to a specified value*/
  case class Ne(pattern: JsValue) extends SimpleConstraint(NeConstraint.key) {
    override def matches(value: JsValue): Boolean = !JsValueMatch.matches(value, pattern)
  }

  object Ne {
    def apply(value: String): Ne = new Ne(JsString(value))
    def apply(value: Int): Ne = new Ne(JsNumber(value))
    def apply(value: Double): Ne = new Ne(JsNumber(value))
    def apply(value: Boolean): Ne = new Ne(JsBoolean(value))
  }

  /**Matches values that are less than a specified value*/
  case class Lt(pattern: JsValue) extends SimpleConstraint(LtConstraint.key) {
    override def matches(value: JsValue): Boolean = JsValueOrdering.lt(value, pattern)
  }

  object Lt {
    def apply(value: Int): Lt = new Lt(JsNumber(value))
    def apply(value: Double): Lt = new Lt(JsNumber(value))
  }

  /**Matches values that are greater than a specified value*/
  case class Gt(pattern: JsValue) extends SimpleConstraint(GtConstraint.key) {
    override def matches(value: JsValue): Boolean = JsValueOrdering.gt(value, pattern)
  }

  object Gt {
    def apply(value: Int): Gt = new Gt(JsNumber(value))
    def apply(value: Double): Gt = new Gt(JsNumber(value))
  }

  /**Matches values that are less than or equal to a specified value*/
  case class Lte(pattern: JsValue) extends SimpleConstraint(LteConstraint.key) {
    override def matches(value: JsValue): Boolean = JsValueOrdering.lteq(value, pattern)
  }

  object Lte {
    def apply(value: Int): Lte = new Lte(JsNumber(value))
    def apply(value: Double): Lte = new Lte(JsNumber(value))
  }

  /**Matches values that are greater than or equal to a specified value*/
  case class Gte(pattern: JsValue) extends SimpleConstraint(GteConstraint.key) {
    override def matches(value: JsValue): Boolean = JsValueOrdering.gteq(value, pattern)
  }

  object Gte {
    def apply(value: Int): Gte = new Gte(JsNumber(value))
    def apply(value: Double): Gte = new Gte(JsNumber(value))
  }

}

trait CriteriaImplicits {

  implicit class TryAsCriteria(t: Try[Criteria]) extends Criteria {
    override def matches(document: JsObject): Boolean = t match {
      case Success(c) => c.matches(document)
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

  implicit class OptionalCriteria(a: Option[Criteria]) {
    def &&(b: Option[Criteria]): Option[Criteria] = (a, b) match {
      case (Some(c1), Some(c2)) => Option(c1 && c2)
      case (None, b) => b
      case (a, None) => a
      case _ => None
    }
    def ||(b: Option[Criteria]): Option[Criteria] = (a, b) match {
      case (Some(c1), Some(c2)) => Option(c1 || c2)
      case (None, b) => b
      case (a, None) => a
      case _ => None
    }
  }
}

object CriteriaUtils {

  trait PathBased {
    val path: String
    lazy val jsPath: JsPath = path.split('.').foldLeft(JsPath())((a, p) => a \ p)
  }

  trait PathValueCriteria extends Criteria with PathBased {
    def matchValue(value: JsValue): Boolean

    override def matches(document: JsObject): Boolean = jsPath.asSingleJson(document) match {
      case JsDefined(value) => matchValue(value)
      case _ => missingValueMatchResult
    }
  }

  trait ConstraintExtractor[A] {
    val key: String
    def extract(value: JsValue, obj: JsObject): A

    final def unapply(obj: JsObject): Option[A] =
      obj.value.get(key).map(value => extract(value, obj))
  }

  abstract class SimpleConstraintExtractor(val key: String) extends ConstraintExtractor[JsValue] {
    override def extract(value: JsValue, obj: JsObject): JsValue = value
  }

  /**Single value constraint*/
  abstract class SimpleConstraint(key: String) extends Constraint {
    val pattern: JsValue
    override def toJson = Json.obj(key -> pattern)
  }

  object JsValueMatch {
    def matches(value: JsValue, pattern: JsValue): Boolean = (value, pattern) match {
      case (JsNull, JsNull) => true
      case (JsString(s1), JsString(s2)) => s1 == s2
      case (JsBoolean(b1), JsBoolean(b2)) => b1 == b2
      case (JsNumber(n1), JsNumber(n2)) => n1 == n2
      //Equality matches on the array require that the value array field match exactly pattern array
      //or the value array contains an element that equals the pattern array
      case (JsArray(seq1), pat @ JsArray(seq2)) => (seq1 == seq2) || (seq1 contains pattern)
      //or the value array contains a pattern element
      case (JsArray(seq1), pat) => seq1 contains pattern
      //Equality matches on an embedded document require an exact match, including the field order
      case (c: JsObject, d: JsObject) => c.fields == d.fields
      case _ => false
    }
  }

  implicit object JsValueOrdering extends Ordering[JsValue] {
    override def compare(value: JsValue, pattern: JsValue): Int = (value, pattern) match {
      case (JsNumber(n1), JsNumber(n2)) => n1.compare(n2)
      case (JsString(s1), JsString(s2)) => s1.compare(s2)
      case (JsBoolean(b1), JsBoolean(b2)) => b1.compare(b2)
      case _ => -1
    }
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

  object IsConstraint {
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

  object AndConstraint extends ConstraintExtractor[Seq[JsObject]] {
    val key = "$and"
    override def extract(value: JsValue, obj: JsObject) =
      value match {
        case JsArrayOfObjects(seq) => seq
        case _ => throw new IllegalArgumentException(s"$key query operator must be an array-of-objects")
      }
  }

  object OrConstraint extends ConstraintExtractor[Seq[JsObject]] {
    val key = "$or"
    override def extract(value: JsValue, obj: JsObject) =
      value match {
        case JsArrayOfObjects(seq) => seq
        case _ => throw new IllegalArgumentException(s"$key query operator must be an array-of-objects")
      }
  }

  object RegexConstraint extends ConstraintExtractor[(String, Option[String])] {
    val key = "$regex"
    val optionsKey = "$options"
    override def extract(value: JsValue, obj: JsObject): (String, Option[String]) = {
      val pattern = value.as[String]
      val optionsOpt = Option(obj).flatMap(o => o.value.get(optionsKey).map(_.as[String]))
      (pattern, optionsOpt)
    }
  }

  object NotConstraint extends ConstraintExtractor[JsObject] {
    val key = "$not"
    override def extract(value: JsValue, obj: JsObject): JsObject = {
      value.as[JsObject]
    }
  }

  object EqConstraint extends SimpleConstraintExtractor("$eq")

  object NeConstraint extends SimpleConstraintExtractor("$ne")

  object LtConstraint extends SimpleConstraintExtractor("$lt")

  object GtConstraint extends SimpleConstraintExtractor("$gt")

  object LteConstraint extends SimpleConstraintExtractor("$lte")

  object GteConstraint extends SimpleConstraintExtractor("$gte")

}