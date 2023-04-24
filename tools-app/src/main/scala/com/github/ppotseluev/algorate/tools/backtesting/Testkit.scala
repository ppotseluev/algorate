package com.github.ppotseluev.algorate.tools.backtesting

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.FullStrategy
import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries

class Testkit[F[_]: Async: Parallel](
    maxConcurrent: Int = 8,
    logProgress: Boolean = true,
    skipNotFound: Boolean = false
)(implicit factory: Factory[F]) {

  private implicit val barSeriesProvider: BarSeriesProvider[F] =
    new BarSeriesProvider(factory.archive)

  def test(periods: List[DaysInterval], assets: List[TradingAsset])(implicit
      strategy: BarSeries => FullStrategy
  ): F[SectorsResults] =
    periods
      .traverse(test(_, assets))
      .map(_.combineAll)

  private def test(done: AtomicInteger, total: Int)(implicit strategy: BarSeries => FullStrategy) =
    (asset: TradingAsset, series: BarSeries) =>
      StrategyTester[F](strategy).test(series, asset).map { stats =>
        val results = SectorsResults(asset, stats)
        if (logProgress) {
          println(s"done: ${(done.incrementAndGet().toDouble * 100 / total).toInt}%")
        }
        results
      }

  def test(period: DaysInterval, assets: List[TradingAsset])(implicit
      barSeriesProvider: BarSeriesProvider[F],
      strategy: BarSeries => FullStrategy
  ): F[SectorsResults] = {
    val interval = CandlesInterval(
      interval = period,
      resolution = CandleResolution.OneMinute
    )
    val counter = new AtomicInteger
    barSeriesProvider
      .streamBarSeries(assets, interval, maxConcurrent, skipNotFound)
      .parEvalMapUnordered(maxConcurrent)(test(counter, assets.size).tupled)
      .compile
      .toList
      .map(_.combineAll)
  }

}
