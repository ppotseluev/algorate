package com.github.ppotseluev.algorate.trader.feature

import com.github.ppotseluev.algorate.trader.feature.Feature.Parse
import scala.util.Try

abstract class Feature[T: Parse] extends (() => T) {
  def name: String

  def set(value: T): Unit

  def set(str: String): Either[String, Unit] =
    Parse[T].apply(str).map(set)
}

object Feature {
  trait Parse[T] {
    def apply(str: String): Either[String, T]
  }

  object Parse {
    def apply[T: Parse]: Parse[T] = implicitly[Parse[T]]

    private def parse[T](f: String => T): Parse[T] = str =>
      Try(f(str)).toEither.left.map(_.toString)

    implicit val double: Parse[Double] = parse(_.toDouble)
    implicit val boolean: Parse[Boolean] = parse(_.toBoolean)
  }
}
