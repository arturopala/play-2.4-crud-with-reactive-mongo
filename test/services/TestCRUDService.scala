package services

import models.Identity
import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }

/**
 * Test {{CRUDService}} impl backed by a mutable thread-safe Map
 */
class TestCRUDService[E, ID](implicit identity: Identity[E, ID])
    extends CRUDService[E, ID] {

  import play.api.libs.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val map: scala.collection.mutable.Map[ID, E] = scala.collection.concurrent.TrieMap()

  override def findById(id: ID): Future[Option[E]] = Future.successful(map.get(id))

  override def findByCriteria(criteria: Map[String, Any]): Future[Traversable[E]] = {
    def matches(entity: E): Boolean = {
      criteria.forall {
        case (attr, value) =>
          Try(entity.getClass.getDeclaredField(attr)) map (f => { f.setAccessible(true); f.get(entity) }) match {
            case Success(obj) =>
              obj match {
                case Some(o) => value match {
                  case Some(v) => o == v
                  case v => o == v
                }
                case o => value match {
                  case Some(v) => o == v
                  case v => o == v
                }
              }
            case Failure(e) =>
              false
          }
      }
    }
    Future.successful(map.values.filter(matches))
  }

  override def create(entity: E): Future[Either[String, ID]] = {
    val criteria = convertToCriteria(identity.clear(entity))
    findByCriteria(criteria).map {
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
    if (map.contains(id)) {
      map.remove(id)
      Future.successful(Right(id))
    } else {
      Future.successful(Left("entity not found"))
    }
  }

  def convertToCriteria(e: E): Map[String, Any] = {
    Try(e.getClass.getDeclaredFields).map(s => s.map(f => { f.setAccessible(true); (f.getName, f.get(e)) })) match {
      case Success(coll) => Map(coll: _*) filterNot { case (k, v) => v == null || v == None }
      case Failure(err) => throw err
    }
  }
}