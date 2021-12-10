package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.{InstrumentId, Order, OrderId}
import com.github.ppotseluev.algorate.util.Interval
import fs2.Stream

import java.time.OffsetDateTime

trait Broker[F[_]] {
  def placeOrder(order: Order): F[OrderId]

  def getData(
      instrumentId: InstrumentId,
      interval: Option[Interval[OffsetDateTime]]
  ): F[Stream[F, Point]]
}
