package services

import models._

trait VesselsService extends Service[Vessel]

import models.ModelBSONHandlers._
import reactivemongo.api.DB

class VesselsMongoService(db: DB) extends MongoService[Vessel] with VesselsService {

  override val collection = db.collection("vessels")

}
