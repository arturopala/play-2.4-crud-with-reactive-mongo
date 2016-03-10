package services

import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._

/**
 * Generic async CRUD service trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait CRUDService[E, ID] {

  def read(id: ID)(implicit ec: ExecutionContext): Future[Option[E]]
  def search(criteria: JsObject, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]]
  def search(criteria: Map[String, Any], limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]]
  def create(entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]]
  def update(id: ID, entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]]
  def delete(id: ID)(implicit ec: ExecutionContext): Future[Either[String, ID]]
}

import models.Identity
import reactivemongo.api._

/**
 * Abstract {{CRUDService}} impl backed by JSONCollection
 */
abstract class MongoCRUDService[E: Format, ID: Format](
    implicit identity: Identity[E, ID]) extends CRUDService[E, ID] {

  import reactivemongo.play.json._
  import reactivemongo.play.json.collection.JSONCollection

  /** Mongo collection deserializable to [E] */
  def collection(implicit ec: ExecutionContext): Future[JSONCollection]

  def read(id: ID)(implicit ec: ExecutionContext): Future[Option[E]] = collection.flatMap(_.find(Json.obj(identity.name -> id)).one[E])

  def search(criteria: Map[String, Any], limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]] = search(CriteriaJSONWriter.writes(criteria), limit)

  def search(criteria: JsObject, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]] =
    collection.flatMap(_.find(criteria).
      cursor[E](readPreference = ReadPreference.nearest).
      collect[List](limit))

  def create(entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    search(Json.toJson(
      identity.clear(entity)).as[JsObject], 1).flatMap {
      case t if t.size > 0 =>
        Future.successful(Right(identity.of(t.head).get)) // let's be idempotent
      case _ => {
        val id = identity.next
        val doc = Json.toJson(identity.set(entity, id)).as[JsObject]

        collection.flatMap(_.insert(doc).map {
          case le if le.ok == true => Right(id)
          case le => Left(le.message)
        })
      }
    }
  }

  def update(id: ID, entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
    collection.flatMap(_.update(Json.obj(identity.name -> id), doc) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    })
  }

  def delete(id: ID)(implicit ec: ExecutionContext): Future[Either[String, ID]] = collection.flatMap(_.remove(Json.obj(identity.name -> id)) map {
    case le if le.ok == true => Right(id)
    case le => Left(le.message)
  })
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
