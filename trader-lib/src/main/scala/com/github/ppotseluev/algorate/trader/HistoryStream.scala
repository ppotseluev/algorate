package com.github.ppotseluev.algorate.trader

import cats.effect.kernel.Temporal
import com.github.ppotseluev.algorate.BarInfo
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import fs2.Stream
import java.time.LocalDate
import scala.concurrent.duration.FiniteDuration

/**
 * Utils to make stream from the historical data
 */
object HistoryStream {

  def make[F[_]: Temporal](
      asset: TradingAsset,
      broker: Broker[F],
      from: LocalDate,
      to: LocalDate,
      rate: FiniteDuration
  ): Stream[F, BarInfo] = {
    val candlesInterval = CandlesInterval(
      interval = DaysInterval(from, to),
      resolution = OneMinute
    )
    Stream
      .eval(broker.getData(asset, candlesInterval))
      .flatMap(Stream.emits(_))
      .map(BarInfo(asset.instrumentId, _))
      .meteredStartImmediately(rate)
  }
}
