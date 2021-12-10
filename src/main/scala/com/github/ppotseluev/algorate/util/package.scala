package com.github.ppotseluev.algorate

import cats.effect.Sync
import cats.effect.kernel.Async

import java.util.concurrent.CompletableFuture

package object util {
  def fromJavaFuture[F[_]: Async, T](future: => CompletableFuture[T]): F[T] =
    Async[F].fromCompletableFuture(Sync[F].delay(future))
}
