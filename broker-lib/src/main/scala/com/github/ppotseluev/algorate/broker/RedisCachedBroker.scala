package com.github.ppotseluev.algorate.broker

import cats.Monad
import cats.Parallel
import cats.implicits._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Order
import com.github.ppotseluev.algorate.OrderId
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.Day
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.typesafe.scalalogging.LazyLogging
import dev.profunktor.redis4cats.RedisCommands

class RedisCachedBroker[F[_]: Monad: Parallel](
    broker: Broker[F],
    barsCache: RedisCommands[F, String, List[Bar]]
) extends Broker[F]
    with LazyLogging {
  override def placeOrder(order: Order): F[OrderPlacementInfo] = broker.placeOrder(order)

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
              logger.info(s"Cache miss $instrumentId $day")
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

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    broker.getOrderInfo(orderId)
}
