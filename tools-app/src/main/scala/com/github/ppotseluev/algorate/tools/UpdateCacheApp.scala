package com.github.ppotseluev.algorate.tools

import boopickle.Default.iterablePickler
import cats.effect._
import com.github.ppotseluev.algorate.broker.CachedBroker
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.redis._
import com.github.ppotseluev.algorate.redis.codec._
import com.github.ppotseluev.algorate.server.Codecs._
import com.github.ppotseluev.algorate.server.Factory
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
