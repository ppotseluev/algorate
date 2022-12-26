package com.github.ppotseluev.algorate.broker

import cats.effect.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.typesafe.scalalogging.LazyLogging
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.Share

class LoggingBroker[F[_]: Sync](broker: Broker[F]) extends Broker[F] with LazyLogging {
  override def getAllShares: F[List[Share]] =
    broker.getAllShares

  override def placeOrder(order: Order): F[OrderPlacementInfo] =
    broker.placeOrder(order)

  override def getData(
      instrumentId: InstrumentId,
      interval: Broker.CandlesInterval
  ): F[List[Bar]] = {
    broker.getData(instrumentId, interval).onError { case e =>
      Sync[F].delay(logger.error(s"Failed getData $instrumentId for $interval", e))
    }
  }

  override def getOrderState(orderId: OrderId): F[OrderState] =
    broker.getOrderState(orderId)
}
