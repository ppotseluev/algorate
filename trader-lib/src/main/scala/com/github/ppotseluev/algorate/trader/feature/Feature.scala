package com.github.ppotseluev.algorate.trader.feature

import cats.ApplicativeError
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

    implicit val double: Parse[Double] = str => Try(str.toDouble).toEither.left.map(_.getMessage)
  }
}
