import play.api.libs.json._ // JSON library
import play.api.libs.functional.syntax._ // Combinator syntax

package object models {

  case class LatLng(latitude: Double, longitude: Double) {
    require(latitude >= -90 && latitude <= 90, s"Latitude value must be in the range [-90, 90] but was $latitude.")
    require(longitude >= -180 && longitude < 180, s"Longitude value must be in the range [-180, 180) but was $longitude.")
  }
  object LatLng {

    object LatLngWrites extends Writes[LatLng] {
      def writes(l: LatLng): JsArray = JsArray(Seq(JsNumber(l.latitude), JsNumber(l.longitude)))
    }

    object LatLngReads extends Reads[LatLng] {
      def reads(json: JsValue) = json match {
        case JsArray(Seq(JsNumber(a), JsNumber(b))) => JsSuccess(LatLng(a.toDouble, b.toDouble))
        case _ => JsError("error.expected.jsarray")
      }
    }

    implicit val LatLngFormat: Format[LatLng] = Format(LatLngReads, LatLngWrites)
  }

  case class Position(location: LatLng, time: Long)
  object Position {
    implicit val PositionFormat = Json.format[Position]
  }

  case class Vessel(name: String, width: Double, length: Double, draft: Double, lastSeenPosition: Position)
  object Vessel {
    implicit val VesselFormat = Json.format[Vessel]
  }

}
