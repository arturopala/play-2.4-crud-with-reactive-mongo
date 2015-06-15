package models

import reactivemongo.bson._
import java.util.UUID

/**
 * BSON to/from conversions for model parts
 */
object ModelBSONHandlers {

  implicit object BSONUUIDHandler extends BSONHandler[BSONString, UUID] {
    def read(string: BSONString) = UUID.fromString(string.value)
    def write(uuid: UUID) = BSONString(uuid.toString)
  }

  implicit object LatLngBSONReader extends BSONDocumentReader[LatLng] {
    def read(doc: BSONDocument): LatLng = LatLng(
      doc.getAs[Double]("0").get,
      doc.getAs[Double]("1").get
    )
  }
  implicit object LatLngBSONWriter extends BSONDocumentWriter[LatLng] {
    def write(elem: LatLng): BSONDocument =
      BSONDocument("0" -> elem.latitude, "1" -> elem.longitude)
  }

  implicit object PositionBSONReader extends BSONDocumentReader[Position] {
    def read(doc: BSONDocument): Position = Position(
      doc.getAs[LatLng]("location").get,
      doc.getAs[Long]("time").get
    )
  }
  implicit object PositionBSONWriter extends BSONDocumentWriter[Position] {
    def write(elem: Position): BSONDocument =
      BSONDocument("location" -> elem.location, "time" -> elem.time)
  }

  implicit object VesselBSONReader extends BSONDocumentReader[Vessel] {
    def read(doc: BSONDocument): Vessel = Vessel(
      doc.getAs[UUID]("uuid"),
      doc.getAs[String]("name").get,
      doc.getAs[Double]("width").get,
      doc.getAs[Double]("length").get,
      doc.getAs[Double]("draft").get,
      doc.getAs[Position]("lastSeenPosition")
    )
  }
  implicit object VesselBSONWriter extends BSONDocumentWriter[Vessel] {
    def write(elem: Vessel): BSONDocument =
      BSONDocument(
        "uuid" -> elem.uuid,
        "name" -> elem.name,
        "width" -> elem.width,
        "length" -> elem.length,
        "draft" -> elem.draft,
        "lastSeenPosition" -> elem.lastSeenPosition
      )
  }

}