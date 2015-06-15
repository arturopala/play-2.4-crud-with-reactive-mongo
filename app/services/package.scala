package object services {
  import reactivemongo.bson._

  type IdBSONHandler[T] = BSONHandler[BSONString, T]
}