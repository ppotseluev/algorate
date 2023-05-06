package com.github.ppotseluev.algorate.tools.backtesting

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import com.github.ppotseluev.algorate.{AssetData, TradingAsset}
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.{FullStrategy, StrategyBuilder}

import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries

import scala.concurrent.duration._

class Testkit[F[_]: Async: Parallel](
    maxConcurrentAssets: Int = 8,
    assetParallelism: Int = 1,
    logProgress: Boolean = true,
    skipNotFound: Boolean = false
)(implicit factory: Factory[F]) {

  private implicit val barSeriesProvider: BarSeriesProvider[F] =
    new BarSeriesProvider(factory.archive)

  def test(intervals: List[CandlesInterval], assets: List[TradingAsset])(implicit
      strategy: StrategyBuilder
  ): F[SectorsResults] =
    intervals
      .traverse(test(_, assets))
      .map(_.combineAll)

  private def testOne(done: AtomicInteger, total: Int)(implicit
      strategy: StrategyBuilder
  ) = (assetData: AssetData) => {
    val asset = assetData.asset
    val series = assetData.barSeries
    println(s"Going to test ${series.getName}")
    StrategyTester[F](strategy, maxParallelism = assetParallelism).test(assetData).map {
      stats =>
        val results = SectorsResults(asset, stats)
        if (logProgress) {
          println(
            s"done: ${(done.incrementAndGet().toDouble * 100 / total).toInt}% (${series.getName})"
          )
        }
        results
    }
  }

  private def testStrategies(done: AtomicInteger, total: Int)(
      strategies: Map[Int, StrategyBuilder]
  )(assetData: AssetData): F[Map[Int, SectorsResults]] =
    strategies.toVector
      .traverse { case (i, s) =>
        testOne(done, total)(s)(assetData).map(i -> _)
      }
      .map(_.toMap)

  def test(interval: CandlesInterval, assets: List[TradingAsset])(implicit
      strategy: StrategyBuilder
  ): F[SectorsResults] = {
    val counter = new AtomicInteger
    barSeriesProvider
      .streamBarSeries(assets, interval, maxConcurrentAssets, skipNotFound)
      .parEvalMapUnordered(maxConcurrentAssets)(testOne(counter, assets.size))
      .compile
      .toList
      .map(_.combineAll)
  }

  def batchTest(interval: CandlesInterval, assets: List[TradingAsset])(
      strategies: List[StrategyBuilder]
  ): F[List[SectorsResults]] = {
    val strategiesMap = strategies.zipWithIndex.swapF.toMap
    val start = System.currentTimeMillis()
    val counter = new AtomicInteger
    val total = assets.size * strategies.size
    barSeriesProvider
      .streamBarSeries(assets, interval, maxConcurrentAssets, skipNotFound)
      .parEvalMapUnordered(maxConcurrentAssets)(
        testStrategies(counter, total)(strategiesMap)
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
