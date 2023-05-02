package com.github.ppotseluev.algorate.tools.backtesting

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.FullStrategy

import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries

import scala.concurrent.duration._

class Testkit[F[_]: Async: Parallel](
    maxConcurrentAssets: Int = 1,
    logProgress: Boolean = true,
    skipNotFound: Boolean = false
)(implicit factory: Factory[F]) {

  private implicit val barSeriesProvider: BarSeriesProvider[F] =
    new BarSeriesProvider(factory.archive)

  def test(intervals: List[CandlesInterval], assets: List[TradingAsset])(implicit
      strategy: BarSeries => FullStrategy
  ): F[SectorsResults] =
    intervals
      .traverse(test(_, assets))
      .map(_.combineAll)

  private def testOne(done: AtomicInteger, total: Int)(implicit
      strategy: BarSeries => FullStrategy
  ) =
    (asset: TradingAsset, series: BarSeries) => {
      println(s"Going to test ${series.getName}")
      StrategyTester[F](strategy).test(series, asset).map { stats =>
        val results = SectorsResults(asset, stats)
        if (logProgress) {
          println(s"done: ${(done.incrementAndGet().toDouble * 100 / total).toInt}% (${series.getName})")
        }
        results
      }
    }

  private def testStrategies(done: AtomicInteger, total: Int)(
      strategies: Map[Int, BarSeries => FullStrategy]
  )(asset: TradingAsset, barSeries: BarSeries): F[Map[Int, SectorsResults]] =
    strategies.toVector
      .traverse { case (i, s) =>
        testOne(done, total)(s)(asset, barSeries).map(i -> _)
      }
      .map(_.toMap)

  def test(interval: CandlesInterval, assets: List[TradingAsset])(implicit
      strategy: BarSeries => FullStrategy
  ): F[SectorsResults] = {
    val counter = new AtomicInteger
    barSeriesProvider
      .streamBarSeries(assets, interval, maxConcurrentAssets, skipNotFound)
      .parEvalMapUnordered(maxConcurrentAssets)(testOne(counter, assets.size).tupled)
      .compile
      .toList
      .map(_.combineAll)
  }

  def batchTest(period: DaysInterval, assets: List[TradingAsset])(
      strategies: List[BarSeries => FullStrategy]
  ): F[List[SectorsResults]] = {
    val strategiesMap = strategies.zipWithIndex.swapF.toMap
    val start = System.currentTimeMillis()
    val interval = CandlesInterval(
      interval = period,
      resolution = CandleResolution.OneMinute
    )
    val counter = new AtomicInteger
    val total = assets.size * strategies.size
    barSeriesProvider
      .streamBarSeries(assets, interval, maxConcurrentAssets, skipNotFound)
      .parEvalMapUnordered(maxConcurrentAssets)(
        (testStrategies(counter, total)(strategiesMap) _).tupled
      )
      .compile
      .toList
      .map { assetsResults =>
        assetsResults.flatten.groupBy(_._1).view.mapValues(_.foldMap(_._2))
      }
      .map { res =>
        val duration = System.currentTimeMillis() - start
        println(s"Strategies testing took ${duration.millis.pretty}")
        res.toList.sortBy(_._1).map(_._2)
      }
  }

}
