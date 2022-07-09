package com.github.ppotseluev.algorate.util

import cats.effect.IO
import cats.effect.unsafe

trait Awaitable[F[_]] {
  def await[T](f: F[T]): T
}

object Awaitable {
  def apply[F[_]: Awaitable]: Awaitable[F] = implicitly

  implicit def io(implicit runtime: unsafe.IORuntime): Awaitable[IO] = new Awaitable[IO] {
    override def await[T](f: IO[T]): T = f.unsafeRunSync()
  }
}
