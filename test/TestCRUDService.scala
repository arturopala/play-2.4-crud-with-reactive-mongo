package services

import scala.util.{ Try, Success, Failure }
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

import models.Identity

/**
 * Test {{CRUDService}} impl backed by a mutable thread-safe Map
 */
class TestCRUDService[E: Format, ID](implicit identity: Identity[E, ID])
    extends CRUDService[E, ID] {

  import play.api.libs.json._

  val map: scala.collection.mutable.Map[ID, E] = scala.collection.concurrent.TrieMap()

  def create(entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    val criteria = Json.toJson(identity.clear(entity)).as[JsObject]
    search(criteria, 1) map {
      case t if t.size > 0 =>
        Right(identity.of(t.head).get)
      case _ =>
        val id = identity.next
        val e = identity.set(identity.clear(entity), id)
        map(id) = e
        Right(id)
    }
  }

  def read(id: ID)(implicit ec: ExecutionContext): Future[Option[E]] = Future.successful(map.get(id))

  def update(id: ID, entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    if (map.contains(id)) {
      map(id) = identity.set(identity.clear(entity), id)
      Future.successful(Right(id))
    } else {
      Future.successful(Left("entity not found"))
    }
  }

  def delete(id: ID)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    map.remove(id)
    Future.successful(Right(id)) //be idempotent
  }

  def search(criteria: JsObject, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]] = {
    Future.successful(map.values.filter(matches(criteria)).take(limit))
  }

  def matches[A: Format](criteria: JsObject)(instance: A): Boolean = {
    matches(criteria, Json.toJson(instance).as[JsObject])
  }

  def matches(criteria: JsObject, instance: JsObject): Boolean = {
    val fields = instance.value
    criteria.fieldSet forall {
      case (name, jsval) =>
        fields.get(name) map (compare(jsval, _)) getOrElse false
    }
  }

  def compare(pattern: JsValue, value: JsValue): Boolean = {
    (pattern, value) match {
      case (JsNull, JsNull) => true
      case (JsString(s1), JsString(s2)) => s1 == s2
      case (JsBoolean(b1), JsBoolean(b2)) => b1 == b2
      case (JsNumber(n1), JsNumber(n2)) => n1 == n2
      case (JsArray(seq1), JsArray(seq2)) => seq1.zip(seq2).forall {
        case (c, i) => compare(c, i)
      }
      case (c: JsObject, i: JsObject) => matches(c, i)
      case _ => false
    }
  }

}
