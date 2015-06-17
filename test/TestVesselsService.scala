import models.Vessel
import java.util.UUID
import services.VesselsService

class TestVesselsService extends TestCRUDService[Vessel, UUID] with VesselsService
