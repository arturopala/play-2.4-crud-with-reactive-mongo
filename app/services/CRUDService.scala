package services

import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.json._
import utils.Criteria

/**
 * Generic async CRUD service trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait CRUDService[E, ID] {

  def create(entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]]
  def read(id: ID)(implicit ec: ExecutionContext): Future[Option[E]]
  def update(id: ID, entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]]
  def delete(id: ID)(implicit ec: ExecutionContext): Future[Either[String, ID]]

  def search(criteria: JsObject, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]]
  def search(criteria: Criteria, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]] =
    this.search(criteria.toJson, limit)
}

import models.Identity
import reactivemongo.api._
import reactivemongo.api.commands.WriteResult

/**
 * Abstract {{CRUDService}} impl backed by JSONCollection
 */
abstract class MongoCRUDService[E: Format, ID: Format](
    implicit
    identity: Identity[E, ID]
) extends CRUDService[E, ID] {

  import reactivemongo.play.json._
  import reactivemongo.play.json.collection.JSONCollection

  /** Mongo collection deserializable to [E] */
  def collection(implicit ec: ExecutionContext): Future[JSONCollection]

  def create(entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    search(Json.toJson(
      identity.clear(entity)
    ).as[JsObject], 1).flatMap {
      case t if t.size > 0 =>
        Future.successful(Right(identity.of(t.head).get)) // let's be idempotent
      case _ => {
        val id = identity.next
        val doc = Json.toJson(identity.set(entity, id)).as[JsObject]

        collection.flatMap(_.insert(doc).map {
          case le @ WriteResult.Message(error) if !le.ok => Left(error)
          case _ => Right(id)
        })
      }
    }
  }

  def read(id: ID)(implicit ec: ExecutionContext): Future[Option[E]] = collection.flatMap(_.find(Json.obj(identity.name -> id)).one[E])

  def update(id: ID, entity: E)(implicit ec: ExecutionContext): Future[Either[String, ID]] = {
    val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
    collection.flatMap(_.update(Json.obj(identity.name -> id), doc) map {
      case le @ WriteResult.Message(error) if !le.ok => Left(error)
      case _ => Right(id)
    })
  }

  def delete(id: ID)(implicit ec: ExecutionContext): Future[Either[String, ID]] = collection.flatMap(_.remove(Json.obj(identity.name -> id)) map {
    case le @ WriteResult.Message(error) if !le.ok => Left(error)
    case _ => Right(id)
  })

  def search(criteria: JsObject, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[E]] =
    collection.flatMap(_.find(criteria).
      cursor[E](readPreference = ReadPreference.nearest).
      collect[List](limit))
}
