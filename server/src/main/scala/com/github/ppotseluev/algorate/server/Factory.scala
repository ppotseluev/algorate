package com.github.ppotseluev.algorate.server

import boopickle.Default.iterablePickler
import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.BrokerAccountId
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.redis.RedisCodecs
import com.github.ppotseluev.algorate.redis.codec._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import java.time.ZoneOffset
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.duration._
import upperbound.Limiter
import upperbound.syntax.rate._

import Codecs._

object Factory {

  def redisClient[F[_]: Async]: Resource[F, RedisClient] =
    RedisClient[F].from("redis://localhost")

  def tinkoffBroker[F[_]: Parallel: Async](
      accountId: BrokerAccountId,
      investApi: InvestApi,
      candlesMinInterval: FiniteDuration = 300 every 1.minute
  ): Resource[F, TinkoffBroker[F]] = {
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
        TinkoffBroker.withLogging(TinkoffBroker[F](tinkoffApi, accountId, ZoneOffset.UTC))
      }
    } yield broker

  }
}
