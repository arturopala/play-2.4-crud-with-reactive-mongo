import controllers._
import services._
import reactivemongo.api._
import com.typesafe.config.ConfigFactory

trait AppComponents {

  import com.softwaremill.macwire.MacwireMacros._

  lazy val config = ConfigFactory.load

  lazy val db: DB = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import reactivemongo.core.nodeset.Authenticate
    import scala.collection.JavaConversions._

    val driver = new MongoDriver
    val connection = driver.connection(
      config.getStringList("mongodb.servers"),
      MongoConnectionOptions(),
      Seq(Authenticate(
        config.getString("mongodb.db"),
        config.getString("mongodb.credentials.username"),
        config.getString("mongodb.credentials.password")
      )
      )
    )
    connection.db(config.getString("mongodb.db"))
  }

  lazy val importController = wire[ImportController]
  lazy val vesselsService: VesselsService = wire[VesselsMongoService]
  lazy val vesselsController = wire[VesselsController]

}
