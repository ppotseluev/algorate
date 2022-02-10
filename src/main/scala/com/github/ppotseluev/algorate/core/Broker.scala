package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.{InstrumentId, Order, OrderId}
import com.github.ppotseluev.algorate.util.Interval

trait Broker[F[_]] {
  def placeOrder(order: Order): F[OrderId]

  def getData(
      instrumentId: InstrumentId,
      interval: Interval.Time
  ): F[Seq[Bar]]
}
