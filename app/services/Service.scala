package services

import models._
import scala.concurrent.Future
import play.api.libs.json._

/**
 * Generic service trait
 */
trait Service[T] {

  def findById(id: String): Future[Option[T]]
  def findByCriteria(criteria: JsValue): Future[List[T]]
  def create(entity: T)(implicit writes: Writes[T], identity: Identity[T]): Future[Either[String, String]]
  def update(id: String, entity: T)(implicit identity: Identity[T]): Future[Either[String, String]]
}

import reactivemongo.api._
import reactivemongo.bson._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
 * Abstract service impl backed by BSONCollection
 */
abstract class MongoService[T: BSONDocumentReader: BSONDocumentWriter] extends Service[T] {
  import reactivemongo.api.collections.default.BSONCollection
  import play.modules.reactivemongo.json.ImplicitBSONHandlers._

  /** Mongo collection deserializable to [T] */
  def collection: BSONCollection

  override def findById(id: String): Future[Option[T]] = collection.
    find(BSONDocument("_id" -> BSONObjectID(id))).
    one[T]

  override def findByCriteria(criteria: JsValue): Future[List[T]] = collection.
    find(criteria).
    cursor[T].
    collect[List]()

  override def create(entity: T)(implicit writes: Writes[T], identity: Identity[T]): Future[Either[String, String]] = {
    findByCriteria(Json.toJson(identity.clear(entity))).flatMap {
      case List(v, _*) =>
        Future.successful(Right(identity.of(v).get)) // let's be idempotent
      case Nil => {
        val writer = implicitly[BSONDocumentWriter[T]]
        val doc = writer.write(identity.set(BSONObjectID.generate.stringify, entity))
        collection.
          insert(doc).
          map {
            case le if le.ok == true => Right(doc.getAs[BSONObjectID]("_id").map(_.stringify).get)
            case le => Left(le.errMsg.get)
          }
      }
    }
  }

  override def update(id: String, entity: T)(implicit identity: Identity[T]): Future[Either[String, String]] = {
    val writer = implicitly[BSONDocumentWriter[T]]
    val doc = writer.write(identity.clear(entity))
    collection.update(BSONDocument("_id" -> BSONObjectID(id)), doc) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.errMsg.get)
    }
  }
}
