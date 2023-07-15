package com.github.ppotseluev.algorate.trader

import cats.effect.kernel.Temporal
import com.github.ppotseluev.algorate.{Bar, BarInfo, TradingAsset}
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
      getData: F[List[Bar]],
      rate: FiniteDuration
  ): Stream[F, BarInfo] =
    Stream
      .eval(getData)
      .flatMap(Stream.emits(_))
      .map(BarInfo(asset.instrumentId, _))
      .meteredStartImmediately(rate)
}
