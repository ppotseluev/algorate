package com.github.ppotseluev.algorate.broker

import com.github.ppotseluev.algorate.{Bar, InstrumentId}
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval

trait BarDataProvider[F[_]] {
  def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[List[Bar]]
}
