package services

import scala.concurrent.{ ExecutionContext, Future }
import java.util.UUID
import models.Vessel
import play.api.libs.json._
import utils.Criteria

case class SearchQuery(
  name: Option[String],
  width: Option[Double],
  length: Option[Double],
  draft: Option[Double]
)

object SearchQuery {
  implicit val format = Json.format[SearchQuery]
}

trait VesselsService extends CRUDService[Vessel, UUID] {

  def search(query: SearchQuery, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[Vessel]] = {

    import Criteria._

    val name = query.name.map(name => If("name" -> Regex("^.*?" + name + ".*$", Some("i"))))
    val width = query.width.map(width => If("width" -> Gt(width - 1)) && If("width" -> Lt(width + 1)))
    val length = query.length.map(length => If("length" -> Gt(length - 1)) && If("length" -> Lt(length + 1)))
    val draft = query.draft.map(draft => If("draft" -> Gt(draft - 1)) && If("draft" -> Lt(draft + 1)))

    val criteria: Option[Criteria] = name || (width && length && draft)

    criteria match {
      case Some(c) =>
        search(c, limit)
      case None =>
        Future.failed(new Exception("None search criteria specified"))
    }
  }

}

import reactivemongo.play.json.collection._
import reactivemongo.api.DB

class VesselsMongoService(db: Future[DB])
    extends MongoCRUDService[Vessel, UUID] with VesselsService {

  override def collection(implicit ec: ExecutionContext): Future[JSONCollection] = db.map(_.collection("vessels"))

}
