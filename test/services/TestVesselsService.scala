package services

import models.Vessel
import java.util.UUID

class TestVesselsService extends TestCRUDService[Vessel, UUID] with VesselsService
