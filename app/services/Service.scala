package services

import models._
import scala.concurrent.Future
import play.api.libs.json._

/**
 * Generic service trait
 */
trait Service[T, ID] {

  def findById(id: ID): Future[Option[T]]
  def findByCriteria(criteria: JsValue): Future[List[T]]
  def create(entity: T)(implicit writes: Writes[T], identity: Identity[T, ID]): Future[Either[String, ID]]
  def update(id: ID, entity: T)(implicit identity: Identity[T, ID]): Future[Either[String, ID]]
}

import reactivemongo.api._
import reactivemongo.bson._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
 * Abstract service impl backed by BSONCollection
 */
abstract class MongoService[T: BSONDocumentReader: BSONDocumentWriter, ID: IdBSONHandler] extends Service[T, ID] {
  import reactivemongo.api.collections.default.BSONCollection
  import play.modules.reactivemongo.json.ImplicitBSONHandlers._

  /** Mongo collection deserializable to [T] */
  def collection: BSONCollection

  override def findById(id: ID): Future[Option[T]] = collection.
    find(BSONDocument("uuid" -> id)).
    one[T]

  override def findByCriteria(criteria: JsValue): Future[List[T]] = collection.
    find(criteria).
    cursor[T].
    collect[List]()

  override def create(entity: T)(implicit writes: Writes[T], identity: Identity[T, ID]): Future[Either[String, ID]] = {
    findByCriteria(Json.toJson(identity.clear(entity))).flatMap {
      case List(first, _*) =>
        Future.successful(Right(identity.of(first).get)) // let's be idempotent
      case Nil => {
        val writer = implicitly[BSONDocumentWriter[T]]
        val doc = writer.write(identity.set(entity, identity.next))
        collection.
          insert(doc).
          map {
            case le if le.ok == true => Right(doc.getAs[ID]("uuid").get)
            case le => Left(le.errMsg.get)
          }
      }
    }
  }

  override def update(id: ID, entity: T)(implicit identity: Identity[T, ID]): Future[Either[String, ID]] = {
    val writer = implicitly[BSONDocumentWriter[T]]
    val doc = writer.write(identity.set(entity, id))
    collection.update(BSONDocument("uuid" -> id), doc) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.errMsg.get)
    }
  }
}
