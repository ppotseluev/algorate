package com.github.ppotseluev.algorate.broker

import cats.effect.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.{Bar, InstrumentId, Order, OrderId, TradingAsset}
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.typesafe.scalalogging.LazyLogging

class LoggingBroker[F[_]: Sync](broker: Broker[F]) extends Broker[F] with LazyLogging {
  override def placeOrder(order: Order): F[OrderPlacementInfo] =
    broker.placeOrder(order)

  override def getData(
      asset: TradingAsset,
      interval: Broker.CandlesInterval
  ): F[List[Bar]] = {
    broker.getData(asset, interval).onError { case e =>
      Sync[F].delay(logger.error(s"Failed getData ${asset.instrumentId} for $interval", e))
    }
  }

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    broker.getOrderInfo(orderId)
}
