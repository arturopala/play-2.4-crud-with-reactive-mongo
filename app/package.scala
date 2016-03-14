package object syntax {

  implicit class Syntax[A, B](source: A) {

    def |>(f: A => B): B = f(source)

  }

}