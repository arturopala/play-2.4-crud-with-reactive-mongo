import controllers._
import services._
import reactivemongo.api._
import com.typesafe.config.ConfigFactory
import util._

trait AppComponents {

  import com.softwaremill.macwire.MacwireMacros._

  lazy val config = ConfigFactory.load

  lazy val db: DB = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import reactivemongo.core.nodeset.Authenticate
    import scala.collection.JavaConversions._

    val driver = new MongoDriver
    val uriString = config.getString("mongodb.uri")
    val uri = MongoConnection.parseURI(uriString) match {
      case Success(uri) => uri
      case Failure(e) => throw new Exception(s"Could not parse mongodb uri $uriString", e)
    }
    println(uri)
    val connection = driver.connection(uri)
    connection(uri.db.get)
  }

  db.collection("vessels")

  lazy val importController = wire[ImportController]
  lazy val vesselsService: VesselsService = wire[VesselsMongoService]
  lazy val vesselsController = wire[VesselsController]

}
