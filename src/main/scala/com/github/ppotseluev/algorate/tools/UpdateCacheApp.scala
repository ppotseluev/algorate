package com.github.ppotseluev.algorate.tools

import boopickle.Default.iterablePickler
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import com.github.ppotseluev.algorate.core.CachedBroker
import com.github.ppotseluev.algorate.core.JsonCodecs._
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.ta4j.test.app.Factory
import com.github.ppotseluev.algorate.util.redis.RedisCodecs
import com.github.ppotseluev.algorate.util.redis.codec._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import scala.io.BufferedSource
import scala.io.Source

object UpdateCacheApp extends IOApp.Simple {
  val boopickleCodec = RedisCodecs.byteBuffer.stringKeys
    .boopickleValues[Seq[Bar]]
  val jsonCodec = RedisCodecs.string.jsonValues[Seq[Bar]]
  private type F[T] = IO[T]

  private val program: Resource[F, F[Unit]] = for {
    redisClient <- Factory.redisClient[F]
    jsonBarsCache <- Redis[F].fromClient(redisClient, jsonCodec)
    boopickleBarsCache <- Redis[F].fromClient(redisClient, boopickleCodec)
    keysSource <- Resource.fromAutoCloseable[F, BufferedSource](
      Sync[F].delay(Source.fromResource("algorate_keys.txt"))
    )
    cacheUpdater = new CacheUpdater[F, String, Seq[Bar]](jsonBarsCache, boopickleBarsCache)
  } yield {
    for {
      keys <- Sync[F].delay(keysSource.getLines().filter(_ != CachedBroker.sharesKey).toList)
      _ <- cacheUpdater.update(keys)
    } yield ()
  }

  override def run: IO[Unit] = program.use(identity)
}
