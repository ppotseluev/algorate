package com.github.ppotseluev.algorate.core
import cats.{Monad, Parallel}
import cats.implicits._
import com.github.ppotseluev.algorate.core.Broker.{CandlesInterval, DaysInterval}
import com.github.ppotseluev.algorate.model.{InstrumentId, Order, OrderId, Ticker}
import dev.profunktor.redis4cats.RedisCommands
import io.chrisdavenport.mules.Cache
import ru.tinkoff.piapi.contract.v1.Share

class CachedBroker[F[_]: Monad: Parallel](
    sharesCache: Cache[F, Unit, List[Share]],
    broker: Broker[F],
    barsCache: RedisCommands[F, String, List[Bar]]
) extends Broker[F] {
  override def getShare(ticker: Ticker): F[Share] = broker.getShare(ticker)

  override def getAllShares: F[List[Share]] =
    sharesCache.lookup(()).flatMap {
      case Some(shares) => shares.pure[F]
      case None         => broker.getAllShares.flatMap(s => sharesCache.insert((), s).as(s))
    }

  override def placeOrder(order: Order): F[OrderId] = broker.placeOrder(order)

  override def getData(
      instrumentId: InstrumentId,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = {
    candlesInterval.interval.iterate
      .parTraverse { day =>
        val key = s"${instrumentId}_${candlesInterval.resolution}_${day.id}"
        for {
          cached <- barsCache.get(key)
          result <- cached match {
            case Some(value) => value.pure[F]
            case None =>
              for {
                newData <- broker.getData(
                  instrumentId,
                  candlesInterval.copy(interval = DaysInterval.singleDay(day))
                )
                _ <- barsCache.set(key, newData)
              } yield newData
          }
        } yield result
      }
      .map(_.flatten)
  }
}
