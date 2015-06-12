import controllers._
import services._

trait AppComponents {

  import com.softwaremill.macwire.MacwireMacros._

  lazy val mainController = wire[MainController]
  lazy val vesselsService = wire[VesselsService]
  lazy val vesselsController = wire[VesselsController]

}
