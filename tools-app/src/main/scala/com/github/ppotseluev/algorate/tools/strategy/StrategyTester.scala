package com.github.ppotseluev.algorate.tools.strategy

import cats.effect.Concurrent
import cats.effect.kernel.Sync
import cats.effect.std.Semaphore
import cats.implicits._
import cats.{Monoid, Parallel}
import com.github.ppotseluev.algorate.{Stats, TradingAsset, TradingStats}
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.{BarSeries, BarSeriesManager}
import org.ta4j.core.Trade.TradeType

private[strategy] case class StrategyTester[F[_]: Parallel: Concurrent](
    impl: StrategyTester.Impl[F],
    maxParallelism: Int,
    minBatchSize: Int
) {
  def test(series: BarSeries, asset: TradingAsset): F[TradingStats] = {
    val batchSize = math.max(minBatchSize, series.getBarCount / maxParallelism)
    val batches = Iterator
      .from(0, step = batchSize)
      .takeWhile(_ <= series.getEndIndex)
      .map { offset =>
        series.getSubSeries(offset, offset + batchSize)
      }
      .toList
    for {
      semaphore <- Semaphore[F](maxParallelism)
      results <- batches.parTraverse(batch => semaphore.permit.use(_ => impl(batch, asset)))
    } yield results.combineAll
  }
}

private[strategy] object StrategyTester {
  def apply[F[_]: Parallel: Concurrent: Sync](
      strategyBuilder: BarSeries => FullStrategy,
      tradingPolicy: Policy = TestSetup.fixedTradeCostPolicy(allowFractionalLots = true),
      maxParallelism: Int = 8,
      minBatchSize: Int = 50_000
  ): StrategyTester[F] =
    new StrategyTester(
      new Impl(strategyBuilder, tradingPolicy),
      maxParallelism = maxParallelism,
      minBatchSize = minBatchSize
    )

  class Impl[F[_]: Sync](strategyBuilder: BarSeries => FullStrategy, tradingPolicy: Policy)
      extends LazyLogging {
    def apply(series: BarSeries, asset: TradingAsset): F[TradingStats] = Sync[F].defer {
      val strategy = strategyBuilder(series)
      val avgPrice =
        series.getFirstBar.getClosePrice
          .plus(series.getLastBar.getClosePrice)
          .dividedBy(series.numOf(2))
      val lots = series.numOf {
        tradingPolicy.apply(TradeRequest(avgPrice.doubleValue, asset.currency)).lots
      }
      if (lots.isPositive) {
        val seriesManager = new BarSeriesManager(series)
        for {
          longRecord <- Sync[F].blocking(
            seriesManager.run(strategy.longStrategy, TradeType.BUY, lots)
          )
          shortRecord <- Sync[F].blocking(
            seriesManager.run(strategy.shortStrategy, TradeType.SELL, lots)
          )
        } yield TradingStats(
          long = Stats.fromRecord(longRecord, series, asset),
          short = Stats.fromRecord(shortRecord, series, asset)
        )
      } else {
        logger.info(s"Skipping ${asset.ticker} ($avgPrice ${asset.currency})")
        Monoid[TradingStats].empty.pure[F]
      }
    }
  }
}
