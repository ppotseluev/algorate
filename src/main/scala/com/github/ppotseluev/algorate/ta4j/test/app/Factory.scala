package com.github.ppotseluev.algorate.ta4j.test.app

import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.implicits._
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.core.CachedBroker
import com.github.ppotseluev.algorate.model.BrokerAccountId
import com.github.ppotseluev.algorate.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import io.chrisdavenport.mules.MemoryCache
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.duration._
import upperbound.Limiter
import upperbound.syntax.rate.rateOps

object Factory {

  def tinkoffBroker[F[_]: Parallel: Async](
      token: String,
      accountId: BrokerAccountId,
      candlesMinInterval: FiniteDuration = 300 every 1.minute
  ): Resource[F, Broker[F]] = {
    val candlesLimiter = Limiter.start[F](candlesMinInterval)
    candlesLimiter.evalMap { limiter =>
      val api = InvestApi.create(token)
      val tinkoffApi = TinkoffApi
        .wrap[F](api)
        .withCandlesLimit(limiter)
      MemoryCache
        .ofSingleImmutableMap[F, Unit, List[Share]](
          defaultExpiration = None
        )
        .map { sharesCache =>
          val impl = new TinkoffBroker(tinkoffApi, accountId)
          new CachedBroker(sharesCache, impl, null) //todo
        }
    }
  }
}
