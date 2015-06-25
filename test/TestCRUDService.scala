import models.Identity
import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }
import services.CRUDService

/**
 * Test {{CRUDService}} impl backed by a mutable thread-safe Map
 */
class TestCRUDService[E, ID](implicit identity: Identity[E, ID])
    extends CRUDService[E, ID] {

  import play.api.libs.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  val map: scala.collection.mutable.Map[ID, E] = scala.collection.concurrent.TrieMap()

  override def findById(id: ID): Future[Option[E]] = Future.successful(map.get(id))

  override def findByCriteria(criteria: Map[String, Any], limit: Int): Future[Traversable[E]] = {
    criteria.get("$query") match {
      case None => Future.successful(map.values.filter(matches(criteria)).take(limit))
      case Some(json: JsObject) => Future.successful(map.values.filter(matches(toCriteria(json))).take(limit))
      case _ => ???
    }
  }

  override def create(entity: E): Future[Either[String, ID]] = {
    val criteria = toCriteria(identity.clear(entity))
    findByCriteria(criteria, 1).map {
      case t if t.size > 0 =>
        Right(identity.of(t.head).get)
      case _ =>
        val id = identity.next
        val e = identity.set(identity.clear(entity), id)
        map(id) = e
        Right(id)
    }
  }

  override def update(id: ID, entity: E): Future[Either[String, ID]] = {
    if (map.contains(id)) {
      map(id) = identity.set(identity.clear(entity), id)
      Future.successful(Right(id))
    } else {
      Future.successful(Left("entity not found"))
    }
  }

  override def delete(id: ID): Future[Either[String, ID]] = {
    map.remove(id)
    Future.successful(Right(id)) //be idempotent
  }

  def matches(criteria: Map[String, Any])(instance: Any): Boolean = {
    instance match {
      case Some(i) => matches(criteria)(i)
      case _ =>
        criteria.forall {
          case (attrName, pattern) =>
            Try(instance.getClass.getDeclaredField(attrName)) map (f => { f.setAccessible(true); f.get(instance) }) match {
              case Success(instanceFieldValue) => compare(pattern, instanceFieldValue)
              case Failure(e) => false
            }
        }
    }
  }

  def compare(pattern: Any, instance: Any): Boolean = {
    pattern match {
      case Some(p) => compare(p, instance)
      case criteria: Map[String, Any] @unchecked => matches(criteria)(instance)
      case patterns: Seq[_] => // case when we compare list of values to object
        Try(instance.getClass.getDeclaredFields()) map (s => s map (f => { f.setAccessible(true); f.get(instance) })) match {
          case Success(values) => patterns.zip(values).forall { case (p, i) => compare(p, i) }
          case Failure(e) => false
        }
      case p => instance match {
        case values: Seq[_] if p.isInstanceOf[Seq[_]] => p.asInstanceOf[Seq[_]].zip(values).forall { case (p, i) => compare(p, i) }
        case Some(i) => p == i
        case i => p == i
      }
    }
  }

  def toCriteria(e: E): Map[String, Any] = {
    Try(e.getClass.getDeclaredFields).map(s => s.map(f => { f.setAccessible(true); (f.getName, f.get(e)) })) match {
      case Success(coll) => Map(coll: _*) filterNot { case (k, v) => v == null || v == None }
      case Failure(err) => throw err
    }
  }

  def toCriteria(json: JsObject): Map[String, Any] = {
    toValue(json).asInstanceOf[Map[String, Any]]
  }

  def toValue: PartialFunction[JsValue, Any] = {
    case JsNull => null
    case JsString(s) => s
    case JsBoolean(b) => b
    case JsNumber(n) => n
    case JsArray(seq) => seq.map(toValue)
    case obj: JsObject => Map(obj.fields.map { case (name, jsValue) => (name, toValue(jsValue)) }: _*)
  }

}