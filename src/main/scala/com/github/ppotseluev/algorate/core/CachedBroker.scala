package com.github.ppotseluev.algorate.core
import cats.Monad
import cats.Parallel
import cats.implicits._
import com.github.ppotseluev.algorate.core.Broker.{CandlesInterval, Day, DaysInterval}
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Ticker
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
    def key(day: Day): String = s"${candlesInterval.resolution}_${day.id}"
    candlesInterval.interval.iterate
      .parTraverse { day =>
        for {
          cached <- barsCache.get(key(day))
          result <- cached match {
            case Some(value) => value.pure[F]
            case None =>
              for {
                newData <- broker.getData(
                  instrumentId,
                  candlesInterval.copy(interval = DaysInterval.singleDay(day))
                )
                _ <- barsCache.set(key(day), newData)
              } yield newData
          }
        } yield result
      }
      .map(_.flatten)
  }
}
