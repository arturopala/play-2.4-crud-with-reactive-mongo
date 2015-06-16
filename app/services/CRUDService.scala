package services

import models._
import scala.concurrent.Future

/**
 * Generic CRUD service trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait CRUDService[E, ID] {

  def findById(id: ID): Future[Option[E]]
  def findByCriteria(criteria: Map[String, Any]): Future[List[E]]
  def create(entity: E)(implicit identity: Identity[E, ID]): Future[Either[String, ID]]
  def update(id: ID, entity: E)(implicit identity: Identity[E, ID]): Future[Either[String, ID]]
  def delete(id: ID): Future[Either[String, ID]]
}

import reactivemongo.api._
import reactivemongo.bson._

/**
 * Abstract service impl backed by BSONCollection
 */
abstract class MongoCRUDService[E: BSONDocumentReader: BSONDocumentWriter, ID: IdBSONHandler] extends CRUDService[E, ID] {

  import reactivemongo.api.collections.default.BSONCollection
  import play.modules.reactivemongo.json.ImplicitBSONHandlers._
  import play.api.libs.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  /** Mongo collection deserializable to [E] */
  def collection: BSONCollection

  override def findById(id: ID): Future[Option[E]] = collection.
    find(BSONDocument("uuid" -> id)).
    one[E]

  override def findByCriteria(criteria: Map[String, Any]): Future[List[E]] = findByCriteria(CriteriaBSONWriter.write(criteria))

  private def findByCriteria(criteria: BSONDocument): Future[List[E]] = collection.
    find(criteria).
    cursor[E].
    collect[List]()

  override def create(entity: E)(implicit identity: Identity[E, ID]): Future[Either[String, ID]] = {
    val writer = implicitly[BSONDocumentWriter[E]]
    findByCriteria(writer.write(identity.clear(entity))).flatMap {
      case List(first, _*) =>
        Future.successful(Right(identity.of(first).get)) // let's be idempotent
      case Nil => {
        val doc = writer.write(identity.set(entity, identity.next))
        collection.
          insert(doc).
          map {
            case le if le.ok == true => Right(doc.getAs[ID]("uuid").get)
            case le => Left(le.message)
          }
      }
    }
  }

  override def update(id: ID, entity: E)(implicit identity: Identity[E, ID]): Future[Either[String, ID]] = {
    val writer = implicitly[BSONDocumentWriter[E]]
    val doc = writer.write(identity.set(entity, id))
    collection.update(BSONDocument("uuid" -> id), doc) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  override def delete(id: ID): Future[Either[String, ID]] = {
    collection.remove(BSONDocument("uuid" -> id)) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  implicit object CriteriaBSONWriter extends BSONDocumentWriter[Map[String, Any]] {
    import reactivemongo.bson.DefaultBSONHandlers._
    def write(criteria: Map[String, Any]): BSONDocument = BSONDocument(criteria.mapValues(toBSONValue(_)))
    val toBSONValue: PartialFunction[Any, BSONValue] = {
      case v: String => BSON.write(v)
      case v: Int => BSON.write(v)
      case v: Long => BSON.write(v)
      case v: Double => BSON.write(v)
      case v: Boolean => BSON.write(v)
      case obj: JsObject => JsObjectWriter.write(obj)
      case map: Map[String, Any] @unchecked => CriteriaBSONWriter.write(map)
      case coll: Traversable[_] => BSONArray(coll.map(toBSONValue(_)))
      case other => throw new IllegalArgumentException(s"Criteria value type not supported: $other")
    }
  }
}
