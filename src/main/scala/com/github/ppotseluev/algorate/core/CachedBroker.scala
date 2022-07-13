package com.github.ppotseluev.algorate.core
import cats.Monad
import cats.Parallel
import cats.implicits._
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.core.Broker.Day
import com.github.ppotseluev.algorate.core.Broker.DaysInterval
import com.github.ppotseluev.algorate.core.CachedBroker.sharesKey
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import dev.profunktor.redis4cats.RedisCommands
import ru.tinkoff.piapi.contract.v1.Share

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class CachedBroker[F[_]: Monad: Parallel](
    sharesCache: RedisCommands[F, String, List[Share]],
    broker: Broker[F],
    barsCache: RedisCommands[F, String, List[Bar]],
    sharesTtl: FiniteDuration = 1.hour
) extends Broker[F] {
  override def getAllShares: F[List[Share]] =
    sharesCache.get(sharesKey).flatMap {
      case Some(shares) => shares.pure[F]
      case None =>
        broker.getAllShares.flatMap { s =>
          sharesCache.setEx(sharesKey, s, sharesTtl).as(s)
        }
    }

  override def placeOrder(order: Order): F[OrderId] = broker.placeOrder(order)

  override def getData(
      instrumentId: InstrumentId,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = {
    def key(day: Day) = s"${instrumentId}_${candlesInterval.resolution}_${day.id}"
    val days = candlesInterval.interval.days
    for {
      cached <- barsCache.mGet(days.map(key).toSet)
      result <- days.parTraverse { day =>
        for {
          result <- cached.get(key(day)) match {
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
    } yield result.flatten
  }
}

object CachedBroker {
  val sharesKey = "shares"
}
