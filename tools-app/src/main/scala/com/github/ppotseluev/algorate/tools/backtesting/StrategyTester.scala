package com.github.ppotseluev.algorate.tools.backtesting

import cats.Monoid
import cats.Parallel
import cats.effect.Concurrent
import cats.effect.kernel.Sync
import cats.effect.std.Semaphore
import cats.implicits._
import com.github.ppotseluev.algorate.AssetData
import com.github.ppotseluev.algorate.Money
import com.github.ppotseluev.algorate.Stats
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.trader.policy.MoneyManagementPolicy
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.cost.CostModel
import org.ta4j.core.cost.LinearTransactionCostModel
import org.ta4j.core.cost.ZeroCostModel

private[backtesting] case class StrategyTester[F[_]: Parallel: Concurrent](
    impl: StrategyTester.Impl[F],
    maxParallelism: Int,
    minBatchSize: Int
) {
  def test(assetData: AssetData): F[TradingStats] = {
    val series = assetData.barSeries
    val asset = assetData.asset
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
      results <- batches
        .map(AssetData(asset, _))
        .parTraverse(batch => semaphore.permit.use(_ => impl(batch, asset)))
    } yield results.combineAll
  }
}

private[backtesting] object StrategyTester {
  def fixedTradeCostPolicy(
      usdTrade: Int = 1_000,
      rubTrade: Int = 10_000,
      allowFractionalLots: Boolean
  ): Policy = {
    val money: Money = Map("usd" -> Int.MaxValue, "rub" -> Int.MaxValue, "usdt" -> Int.MaxValue)
    new MoneyManagementPolicy(() => Some(money))(
      maxPercentage = 1,
      maxAbsolute = Map(
        "usd" -> usdTrade,
        "usdt" -> usdTrade,
        "rub" -> rubTrade
      ),
      allowFractionalLots = allowFractionalLots
    )
  }

  def apply[F[_]: Parallel: Concurrent: Sync](
      strategyBuilder: StrategyBuilder,
      tradingPolicy: Policy = fixedTradeCostPolicy(allowFractionalLots = true),
      maxParallelism: Int = 8,
      minBatchSize: Int = 50_000,
      transactionCostModel: CostModel = new LinearTransactionCostModel(0.0005),
      holdingCostModel: CostModel = new ZeroCostModel
  ): StrategyTester[F] =
    new StrategyTester(
      new Impl(strategyBuilder, tradingPolicy, transactionCostModel, holdingCostModel),
      maxParallelism = maxParallelism,
      minBatchSize = minBatchSize
    )

  class Impl[F[_]: Sync](
      strategyBuilder: StrategyBuilder,
      tradingPolicy: Policy,
      transactionCostModel: CostModel,
      holdingCostModel: CostModel
  ) extends LazyLogging {
    def apply(assetData: AssetData, asset: TradingAsset): F[TradingStats] = Sync[F].defer {
      val series = assetData.barSeries
      val strategy = strategyBuilder(assetData)
      val avgPrice =
        series.getFirstBar.getClosePrice
          .plus(series.getLastBar.getClosePrice)
          .dividedBy(series.numOf(2))
      val lots = series.numOf {
        tradingPolicy.apply(TradeRequest(avgPrice.doubleValue, asset.currency)).lots
      }
      if (lots.isPositive) {
        val seriesManager = new BarSeriesManager(series, transactionCostModel, holdingCostModel)
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
