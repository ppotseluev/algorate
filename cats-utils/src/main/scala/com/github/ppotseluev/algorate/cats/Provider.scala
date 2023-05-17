package com.github.ppotseluev.algorate.cats

import cats.effect.Sync
import cats.effect.Temporal
import fs2.Stream
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

class Provider[F[_]: Sync: Temporal, T](
    pull: F[T],
    initialValue: Option[T] = None,
    updateEvery: FiniteDuration = 30.seconds
) {
  @volatile private var value: Option[T] = initialValue

  private def stream: Stream[F, T] =
    Stream.eval(pull).handleErrorWith { case NonFatal(_) => stream }

  val run: F[Unit] = stream
    .evalMap { value => Sync[F].delay { this.value = Some(value) } }
    .repeat
    .meteredStartImmediately(updateEvery)
    .compile
    .drain

  def get: Option[T] = value
  def getOrElse(default: => T): T = value.getOrElse(default)
}
