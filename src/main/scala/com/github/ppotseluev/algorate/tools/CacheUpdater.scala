package com.github.ppotseluev.algorate.tools

import cats.MonadThrow
import cats.Parallel
import cats.implicits._
import com.github.ppotseluev.algorate.util.redis.RedisCodecs.DecodeException
import dev.profunktor.redis4cats.RedisCommands

class CacheUpdater[F[_]: Parallel: MonadThrow, K, V](
    oldCache: RedisCommands[F, K, V],
    newCache: RedisCommands[F, K, V]
) {

  def update(keys: List[K]): F[Unit] = {
    def update(key: K): F[Unit] =
      for {
        maybeValue <- oldCache.get(key).recover { case DecodeException(_) => None }
        _ <- maybeValue.fold(().pure[F])(newCache.set(key, _))
      } yield ()
    keys.traverse(update).void
  }
}
