package services

import models.Vessel
import java.util.UUID

trait VesselsService extends CRUDService[Vessel, UUID]

import reactivemongo.play.json.collection._
import reactivemongo.api.DB

class VesselsMongoService(db: DB) extends MongoCRUDService[Vessel, UUID] with VesselsService {
  override val collection: JSONCollection = db.collection("vessels")
}
