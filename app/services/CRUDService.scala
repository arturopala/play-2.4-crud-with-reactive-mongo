package services

import scala.concurrent.Future
import play.api.libs.json._

/**
 * Generic async CRUD service trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait CRUDService[E, ID] {

  def read(id: ID): Future[Option[E]]
  def create(entity: E): Future[Either[String, ID]]
  def update(id: ID, entity: E): Future[Either[String, ID]]
  def delete(id: ID): Future[Either[String, ID]]
  def search(criteria: JsObject, limit: Int): Future[Traversable[E]]
}

import models.Identity
import reactivemongo.api._

/**
 * Abstract {{CRUDService}} impl backed by JSONCollection
 */
abstract class MongoCRUDService[E: Format, ID: Format](implicit identity: Identity[E, ID])
    extends CRUDService[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import reactivemongo.play.json.collection.JSONCollection
  import reactivemongo.play.json._

  /** Mongo collection deserializable to [E] */
  def collection: JSONCollection

  override def read(id: ID): Future[Option[E]] = collection.
    find(Json.obj(identity.name -> id)).
    one[E]

  override def search(criteria: JsObject, limit: Int): Future[Traversable[E]] =
    collection.
      find(criteria).
      cursor[E](readPreference = ReadPreference.nearest).
      collect[List](limit)

  override def create(entity: E): Future[Either[String, ID]] = {
    search(Json.toJson(identity.clear(entity)).as[JsObject], 1).flatMap {
      case t if t.size > 0 =>
        Future.successful(Right(identity.of(t.head).get)) // let's be idempotent
      case _ => {
        val id = identity.next
        val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
        collection.
          insert(doc).
          map {
            case le if le.ok == true => Right(id)
            case le => Left(le.message)
          }
      }
    }
  }

  override def update(id: ID, entity: E): Future[Either[String, ID]] = {
    val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
    collection.update(Json.obj(identity.name -> id), doc) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  override def delete(id: ID): Future[Either[String, ID]] = {
    collection.remove(Json.obj(identity.name -> id)) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

}

object CriteriaJSONWriter extends Writes[Map[String, Any]] {
  override def writes(criteria: Map[String, Any]): JsObject = JsObject(criteria.mapValues(toJsValue(_)).toSeq)
  val toJsValue: PartialFunction[Any, JsValue] = {
    case v: String => JsString(v)
    case v: Int => JsNumber(v)
    case v: Long => JsNumber(v)
    case v: Double => JsNumber(v)
    case v: Boolean => JsBoolean(v)
    case obj: JsValue => obj
    case map: Map[String, Any] @unchecked => CriteriaJSONWriter.writes(map)
    case coll: Traversable[_] => JsArray(coll.map(toJsValue(_)).toSeq)
    case null => JsNull
    case other => throw new IllegalArgumentException(s"Criteria value type not supported: $other")
  }
}