package com.github.ppotseluev.algorate.broker

import com.github.ppotseluev.algorate.{Bar, InstrumentId, TradingAsset}
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval

trait BarDataProvider[F[_]] {
  def getData(
      asset: TradingAsset,
      interval: CandlesInterval
  ): F[List[Bar]]
}
