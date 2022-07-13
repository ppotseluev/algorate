package com.github.ppotseluev.algorate.ta4j.test.app

import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import com.github.ppotseluev.algorate.core.Bar
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.core.CachedBroker
import com.github.ppotseluev.algorate.core.JsonCodecs._
import com.github.ppotseluev.algorate.core.LoggingBroker
import com.github.ppotseluev.algorate.model.BrokerAccountId
import com.github.ppotseluev.algorate.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.util.redis.RedisCodecs
import com.github.ppotseluev.algorate.util.redis.RedisJsonCodec._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.Stdout._
import java.time.ZoneOffset
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.duration._
import upperbound.Limiter
import upperbound.syntax.rate.rateOps

object Factory {

  def redisClient[F[_]: Async]: Resource[F, RedisClient] =
    RedisClient[F].from("redis://localhost")

  def redisBarsCache[F[_]: Async](
      client: RedisClient
  ): Resource[F, RedisCommands[F, String, List[Bar]]] = {
    val codec = RedisCodecs[String, String].jsonValues[List[Bar]]
    Redis[F].fromClient(client, codec)
  }

  def redisSharesCache[F[_]: Async](
      client: RedisClient
  ): Resource[F, RedisCommands[F, String, List[Share]]] = {
    val codec = RedisCodecs[String, String].jsonValues[List[Share]]
    Redis[F].fromClient(client, codec)
  }

  def tinkoffBroker[F[_]: Parallel: Async](
      token: String,
      accountId: BrokerAccountId,
      candlesMinInterval: FiniteDuration = 300 every 1.minute
  ): Resource[F, Broker[F]] = {
    for {
      candlesLimiter <- Limiter.start[F](candlesMinInterval)
      redisClient <- redisClient
      barsCache <- redisBarsCache(redisClient)
      sharesCache <- redisSharesCache(redisClient)
      broker = {
        val api = InvestApi.create(token)
        val tinkoffApi = TinkoffApi
          .wrap[F](api)
          .withCandlesLimit(candlesLimiter)
          .withLogging
        val impl = new TinkoffBroker(tinkoffApi, accountId, ZoneOffset.UTC)
        new LoggingBroker(
          new CachedBroker(sharesCache, impl, barsCache)
        )
      }
    } yield broker

  }
}
