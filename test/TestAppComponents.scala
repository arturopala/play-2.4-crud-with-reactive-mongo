import services.VesselsService

trait TestAppComponents extends AppComponents {

  import com.softwaremill.macwire._

  override lazy val vesselsService: VesselsService = wire[TestVesselsService]

}