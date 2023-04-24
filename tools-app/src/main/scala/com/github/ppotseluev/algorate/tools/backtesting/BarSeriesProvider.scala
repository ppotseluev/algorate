package com.github.ppotseluev.algorate.tools.backtesting

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import com.github.ppotseluev.algorate.BarsConverter
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Archive.ArchiveNotFound
import com.github.ppotseluev.algorate.broker.BarDataProvider
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import fs2.Stream
import org.ta4j.core.BarSeries

private[backtesting] class BarSeriesProvider[F[_]: Async: Parallel](
    barDataProvider: BarDataProvider[F]
) {

  def getBarSeries(
      asset: TradingAsset,
      interval: CandlesInterval
  ): F[BarSeries] =
    for {
      bars <- barDataProvider.getData(asset, interval)
    } yield BarsConverter.buildBarSeries(asset.ticker, bars)

  def streamBarSeries(
      shares: List[TradingAsset],
      interval: CandlesInterval,
      maxConcurrent: Int,
      skipNotFound: Boolean
  ): Stream[F, (TradingAsset, BarSeries)] =
    Stream
      .emits(shares)
      .evalMap[F, Option[(TradingAsset, BarSeries)]] { asset =>
        getBarSeries(asset, interval)
          .map { d =>
//            println(s"fetched: ${fetched.incrementAndGet()}")
            (asset -> d).some
          }
          .recover { case _: ArchiveNotFound if skipNotFound => None }
      }
      .flatMap(Stream.fromOption[F].apply(_))
}
