package services

import models.Vessel
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }

class TestVesselsService extends TestCRUDService[Vessel, UUID] with VesselsService
