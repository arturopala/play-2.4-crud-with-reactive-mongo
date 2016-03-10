import scala.concurrent.{ ExecutionContext, Future }

import controllers._
import services._
import reactivemongo.api._
import com.typesafe.config.ConfigFactory

trait AppComponents {
  import com.softwaremill.macwire._

  lazy val config = ConfigFactory.load

  lazy val driver = new MongoDriver

  lazy val db: Future[DefaultDB] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val parsedUri = MongoConnection.parseURI(config getString "mongodb.uri")

    for {
      uri <- Future.fromTry(parsedUri)
      con = driver.connection(uri)
      dn <- Future(uri.db.get)
      db <- con.database(dn)
    } yield db
  }

  lazy val importController = wire[ImportController]
  lazy val vesselsService: VesselsService = wire[VesselsMongoService]
  lazy val vesselsController = wire[VesselsController]
}
