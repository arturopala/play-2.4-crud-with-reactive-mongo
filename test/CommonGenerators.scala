import org.scalacheck._
import models._

trait CommonGenerators {
  val LatGenerator = Gen.chooseNum(-90d, 90d)
  val LngGenerator = Gen.chooseNum(-180d, 180d) suchThat (_ < 180)
  val TimeGenerator = Gen.chooseNum(0L, Long.MaxValue)
  val LatLngGenerator = for { lat <- LatGenerator; lng <- LngGenerator } yield LatLng(lat, lng)
  val PositionGenerator = Gen.option(for { l <- LatLngGenerator; t <- TimeGenerator } yield Position(l, t))
  val NameGenerator = Gen.alphaStr
  val DoubleGenerator = Gen.chooseNum(0d, 1000d) suchThat (_ > 0)
  val VesselGenerator = for (n <- NameGenerator; w <- DoubleGenerator; l <- DoubleGenerator; d <- DoubleGenerator; p <- PositionGenerator) yield Vessel(None, n, w, l, d, p)
}