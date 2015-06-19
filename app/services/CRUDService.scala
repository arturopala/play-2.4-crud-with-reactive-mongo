package services

import scala.concurrent.Future

/**
 * Generic async CRUD service trait
 * @param E type of entity
 * @param ID type of identity of entity (primary key)
 */
trait CRUDService[E, ID] {

  def findById(id: ID): Future[Option[E]]
  def findByCriteria(criteria: Map[String, Any], limit: Int): Future[Traversable[E]]
  def create(entity: E): Future[Either[String, ID]]
  def update(id: ID, entity: E): Future[Either[String, ID]]
  def delete(id: ID): Future[Either[String, ID]]
}

import models.Identity
import reactivemongo.api._
import reactivemongo.bson._

/**
 * Abstract {{CRUDService}} impl backed by BSONCollection
 */
abstract class MongoCRUDService[E: BSONDocumentReader: BSONDocumentWriter, ID: IdBSONHandler](implicit identity: Identity[E, ID])
    extends CRUDService[E, ID] {

  import reactivemongo.api.collections.default.BSONCollection
  import play.modules.reactivemongo.json.ImplicitBSONHandlers._
  import play.api.libs.json._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  /** Mongo collection deserializable to [E] */
  def collection: BSONCollection

  override def findById(id: ID): Future[Option[E]] = collection.
    find(BSONDocument(identity.name -> id)).
    one[E]

  override def findByCriteria(criteria: Map[String, Any], limit: Int): Future[Traversable[E]] = findByCriteria(CriteriaBSONWriter.write(criteria), limit)

  private def findByCriteria(criteria: BSONDocument, limit: Int): Future[Traversable[E]] = collection.
    find(criteria).
    cursor[E].
    collect[List](limit)

  override def create(entity: E): Future[Either[String, ID]] = {
    val writer = implicitly[BSONDocumentWriter[E]]
    findByCriteria(writer.write(identity.clear(entity)), 1).flatMap {
      case t if t.size > 0 =>
        Future.successful(Right(identity.of(t.head).get)) // let's be idempotent
      case _ => {
        val doc = writer.write(identity.set(entity, identity.next))
        collection.
          insert(doc).
          map {
            case le if le.ok == true => Right(doc.getAs[ID](identity.name).get)
            case le => Left(le.message)
          }
      }
    }
  }

  override def update(id: ID, entity: E): Future[Either[String, ID]] = {
    val writer = implicitly[BSONDocumentWriter[E]]
    val doc = writer.write(identity.set(entity, id))
    collection.update(BSONDocument(identity.name -> id), doc) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  override def delete(id: ID): Future[Either[String, ID]] = {
    collection.remove(BSONDocument(identity.name -> id)) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }
}

object CriteriaBSONWriter extends BSONDocumentWriter[Map[String, Any]] {
  import reactivemongo.bson.DefaultBSONHandlers._
  import reactivemongo.api.collections.default.BSONCollection
  import play.modules.reactivemongo.json.ImplicitBSONHandlers._
  import play.api.libs.json._

  def write(criteria: Map[String, Any]): BSONDocument = BSONDocument(criteria.mapValues(toBSONValue(_)))
  val toBSONValue: PartialFunction[Any, BSONValue] = {
    case v: String => BSON.write(v)
    case v: Int => BSON.write(v)
    case v: Long => BSON.write(v)
    case v: Double => BSON.write(v)
    case v: Boolean => BSON.write(v)
    case JsString(s) => BSONString(s)
    case JsNumber(n) => BSONDouble(n.doubleValue)
    case JsBoolean(b) => BSONBoolean(b)
    case JsArray(a) => BSONArray(a.map(toBSONValue))
    case obj: JsObject => BSONDocument(obj.fields.map { case (k, v) => (k, toBSONValue(v)) })
    case map: Map[String, Any] @unchecked => CriteriaBSONWriter.write(map)
    case coll: Traversable[_] => BSONArray(coll.map(toBSONValue))
    case other => throw new IllegalArgumentException(s"Criteria value type not supported: $other")
  }
}

