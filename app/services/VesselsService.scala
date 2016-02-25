package services

import scala.concurrent.{ ExecutionContext, Future }
import java.util.UUID
import models.Vessel

trait VesselsService extends CRUDService[Vessel, UUID]

import reactivemongo.play.json.collection._
import reactivemongo.api.DB

class VesselsMongoService(db: Future[DB])
    extends MongoCRUDService[Vessel, UUID] with VesselsService {

  override def collection(implicit ec: ExecutionContext): Future[JSONCollection] = db.map(_.collection("vessels"))
}
