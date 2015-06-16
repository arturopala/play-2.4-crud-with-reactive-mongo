package controllers

import java.util.UUID
import models.Vessel
import services.VesselsService

class VesselsController(vesselsService: VesselsService)
  extends CRUDController[Vessel, UUID](vesselsService)(routes.VesselsController.one)

