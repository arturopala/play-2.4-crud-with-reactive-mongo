package services

import scala.concurrent.{ ExecutionContext, Future }
import java.util.UUID
import models.Vessel
import play.api.libs.json._

case class SearchQuery(name: String)

object SearchQuery {
  implicit val format = Json.format[SearchQuery]
}

trait VesselsService extends CRUDService[Vessel, UUID] {

  def search(query: SearchQuery, limit: Int)(implicit ec: ExecutionContext): Future[Traversable[Vessel]] = {
    search(Json.obj("name" -> query.name), limit)
  }

}

import reactivemongo.play.json.collection._
import reactivemongo.api.DB

class VesselsMongoService(db: Future[DB])
    extends MongoCRUDService[Vessel, UUID] with VesselsService {

  override def collection(implicit ec: ExecutionContext): Future[JSONCollection] = db.map(_.collection("vessels"))

}
