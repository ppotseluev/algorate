package com.github.ppotseluev.algorate.trader

import cats.effect.kernel.Temporal
import com.github.ppotseluev.algorate.BarInfo
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import fs2.Stream
import scala.concurrent.duration.FiniteDuration

/**
 * Utils to make stream from the historical data
 */
object HistoryStream {

  def make[F[_]: Temporal](
      asset: TradingAsset,
      broker: Broker[F],
      candlesInterval: CandlesInterval,
      rate: FiniteDuration
  ): Stream[F, BarInfo] =
    Stream
      .eval(broker.getData(asset, candlesInterval))
      .flatMap(Stream.emits(_))
      .map(BarInfo(asset.instrumentId, _))
      .meteredStartImmediately(rate)
}
