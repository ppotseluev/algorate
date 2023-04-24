package com.github.ppotseluev.algorate.broker

import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval

trait BarDataProvider[F[_]] {
  def getData(
      asset: TradingAsset,
      interval: CandlesInterval
  ): F[List[Bar]]
}
