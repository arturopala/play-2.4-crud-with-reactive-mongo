package services

import models.Vessel
import java.util.UUID

trait VesselsService extends CRUDService[Vessel, UUID]

import models.ModelBSONHandlers._
import reactivemongo.bson.DefaultBSONHandlers._
import reactivemongo.api.DB

class VesselsMongoService(db: DB) extends MongoCRUDService[Vessel, UUID] with VesselsService {
  override val collection = db.collection("vessels")
}
