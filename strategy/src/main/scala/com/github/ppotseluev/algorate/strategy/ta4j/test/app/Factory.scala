package com.github.ppotseluev.algorate.strategy.ta4j.test.app

import boopickle.Default.iterablePickler
import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import com.github.ppotseluev.algorate.broker.{Broker, CachedBroker, LoggingBroker}
import com.github.ppotseluev.algorate.core.JsonCodecs._
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.BrokerAccountId
import com.github.ppotseluev.algorate.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.util.redis.RedisCodecs
import com.github.ppotseluev.algorate.util.redis.codec._
import dev.profunktor.redis4cats.Redis
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

  def tinkoffBroker[F[_]: Parallel: Async](
      accountId: BrokerAccountId,
      investApi: InvestApi,
      candlesMinInterval: FiniteDuration = 300 every 1.minute
  ): Resource[F, Broker[F]] = {
    for {
      candlesLimiter <- Limiter.start[F](candlesMinInterval)
      redisClient <- redisClient
      barsCache <- Redis[F].fromClient(
        redisClient,
        RedisCodecs.byteBuffer.stringKeys.boopickleValues[List[Bar]]
      )
      sharesCache <- Redis[F].fromClient(
        redisClient,
        RedisCodecs[String, String].jsonValues[List[Share]]
      )
      broker = {
        val tinkoffApi = TinkoffApi
          .wrap[F](investApi)
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
