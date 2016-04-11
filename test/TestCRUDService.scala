package services

import scala.util.{ Try, Success, Failure }
import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._
import utils._

import models.Identity

/**
 * Test {{CRUDService}} impl backed by a mutable thread-safe Map
 */
class TestCRUDService[E: Format, ID](implicit identity: Identity[E, ID])
    extends CRUDService[E, ID] {

  import play.api.libs.json._

  private val map: scala.collection.mutable.Map[ID, JsObject] = scala.collection.concurrent.TrieMap()

  def create(entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    val criteria = Json.toJson(identity.clear(entity)).as[JsObject]
    search(criteria, 1) map {
      case t if t.size > 0 =>
        Right(identity.of(t.head).get)
      case _ =>
        val id = identity.next
        val e = identity.set(identity.clear(entity), id)
        map(id) = Json.toJson(e).as[JsObject]
        Right(id)
    }
  }

  def read(id: ID)(implicit ec: ExecutionContext): Future[Option[E]] =
    Future.successful(map.get(id).map(_.as[E]))

  def update(id: ID, entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    if (map.contains(id)) {
      val e = identity.set(identity.clear(entity), id)
      map(id) = Json.toJson(e).as[JsObject]
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
    Future.fromTry(Criteria(criteria).map(c =>
      map.values.view.filter(c.matches).take(limit).map(_.as[E])))
  }

}
