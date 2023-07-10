package com.github.ppotseluev.algorate.broker

import cats.effect.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.Order
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.typesafe.scalalogging.LazyLogging

trait LoggingBroker[F[_]] extends Broker[F] with LazyLogging {
  implicit def F: Sync[F]

  abstract override def placeOrder(order: Order): F[OrderPlacementInfo] =
    super
      .placeOrder(order)
      .flatMap { res =>
        Sync[F].delay { logger.info(s"Order placed $order, result $res") }.as(res)
      }
      .onError { case e =>
        Sync[F].delay { logger.error(s"Fail to place order $order", e) }
      }

  abstract override def getData(
      asset: TradingAsset,
      interval: Broker.CandlesInterval
  ): F[List[Bar]] = {
    super.getData(asset, interval).onError { case e =>
      Sync[F].delay(logger.error(s"Failed getData ${asset.instrumentId} for $interval", e))
    }
  }
}
