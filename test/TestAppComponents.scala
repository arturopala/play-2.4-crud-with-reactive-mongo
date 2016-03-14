package config

import services.VesselsService

import services._

trait TestAppComponents extends AppComponents {

  import com.softwaremill.macwire._

  override lazy val vesselsService: VesselsService = wire[TestVesselsService]

}