package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Ticker
import com.github.ppotseluev.algorate.util.Interval
import ru.tinkoff.piapi.contract.v1.Share

trait Broker[F[_]] {
  def getShare(ticker: Ticker): F[Share]

  def getAllShares: F[List[Share]] //TODO abstract over tinkoff model

  def placeOrder(order: Order): F[OrderId]

  def getData(
      instrumentId: InstrumentId,
      interval: Interval.Time
  ): F[Seq[Bar]]
}
