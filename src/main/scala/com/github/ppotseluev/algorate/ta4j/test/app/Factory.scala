package com.github.ppotseluev.algorate.ta4j.test.app

import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.implicits._
import com.github.ppotseluev.algorate.core.{Bar, Broker, CachedBroker, LoggingBroker}
import com.github.ppotseluev.algorate.model.BrokerAccountId
import com.github.ppotseluev.algorate.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.util.redis.RedisCodecs
import com.github.ppotseluev.algorate.util.redis.RedisJsonCodec._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.effect.Log.Stdout._
import io.chrisdavenport.mules.MemoryCache
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi

import scala.concurrent.duration._
import upperbound.Limiter
import upperbound.syntax.rate.rateOps

import java.time.ZoneOffset

object Factory {

  def redisBarsCache[F[_]: Async]: Resource[F, RedisCommands[F, String, List[Bar]]] = {
    val codec = RedisCodecs[String, String].jsonValues[List[Bar]]
    Redis[F].simple("redis://localhost", codec)
  }

  def tinkoffBroker[F[_]: Parallel: Async](
      token: String,
      accountId: BrokerAccountId,
      candlesMinInterval: FiniteDuration = 300 every 1.minute
  ): Resource[F, Broker[F]] = {
    for {
      candlesLimiter <- Limiter.start[F](candlesMinInterval)
      redis <- redisBarsCache[F]
      broker <- Resource.eval {
        val api = InvestApi.create(token)
        val tinkoffApi = TinkoffApi
          .wrap[F](api)
          .withCandlesLimit(candlesLimiter)
          .withLogging
        MemoryCache
          .ofSingleImmutableMap[F, Unit, List[Share]](
            defaultExpiration = None
          )
          .map { sharesCache =>
            val impl = new TinkoffBroker(tinkoffApi, accountId, ZoneOffset.UTC)
            new LoggingBroker(
              new CachedBroker(sharesCache, impl, redis)
            )
          }
      }
    } yield broker

  }
}
