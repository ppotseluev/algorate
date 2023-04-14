package com.github.ppotseluev.algorate.broker

import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.typesafe.scalalogging.LazyLogging

class ArchiveCachedBroker[F[_]](broker: Broker[F], archive: Archive[F])
    extends Broker[F]
    with LazyLogging {
  override def placeOrder(order: Order): F[OrderPlacementInfo] = broker.placeOrder(order)

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    broker.getOrderInfo(orderId)

  override def getData(instrumentId: InstrumentId, interval: CandlesInterval): F[List[Bar]] =
    archive.getData(instrumentId, interval)
}
