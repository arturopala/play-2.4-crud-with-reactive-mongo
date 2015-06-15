package services

import models.Vessel
import java.util.UUID

trait VesselsService extends Service[Vessel, UUID]

import models.ModelBSONHandlers._
import reactivemongo.bson.DefaultBSONHandlers._
import reactivemongo.api.DB

class VesselsMongoService(db: DB) extends MongoService[Vessel, UUID] with VesselsService {

  override val collection = db.collection("vessels")

}
