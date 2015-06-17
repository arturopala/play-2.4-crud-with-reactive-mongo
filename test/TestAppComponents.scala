import services.VesselsService

trait TestAppComponents extends AppComponents {

  import com.softwaremill.macwire.MacwireMacros._

  override lazy val vesselsService: VesselsService = wire[TestVesselsService]

}