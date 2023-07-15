package com.github.ppotseluev.algorate.tools.backtesting

import cats.Parallel
import cats.effect.Concurrent
import cats.effect.kernel.Sync
import cats.effect.std.Semaphore
import cats.implicits._
import com.github.ppotseluev.algorate.AssetData
import com.github.ppotseluev.algorate.Money
import com.github.ppotseluev.algorate.Stats
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.ta4j.BarSeriesManager
import com.github.ppotseluev.algorate.trader.policy.MoneyManagementPolicy
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest
import com.typesafe.scalalogging.LazyLogging
import java.util.function.Function
import org.ta4j.core.Bar
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.cost.CostModel
import org.ta4j.core.cost.LinearTransactionCostModel
import org.ta4j.core.cost.ZeroCostModel
import org.ta4j.core.num.Num

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
        .parTraverse(batch => semaphore.permit.use(_ => impl(batch)))
    } yield results.combineAll
  }
}

private[backtesting] object StrategyTester {
  def fixedTradeCostPolicy(
      usdTrade: Int = 1000,
      rubTrade: Int = 10_000
  ): Policy = {
    val money: Money = Map("usd" -> Int.MaxValue, "rub" -> Int.MaxValue, "usdt" -> Int.MaxValue)
    val map: Map[String, () => Double] = Map(
      "usd" -> (() => usdTrade),
      "usdt" -> (() => usdTrade),
      "rub" -> (() => rubTrade)
    )
    new MoneyManagementPolicy(() => Some(money))(
      maxPercentage = 1,
      maxAbsolute = map,
      manualMaxAbsolute = map
    )
  }

  def apply[F[_]: Parallel: Concurrent: Sync](
      strategyBuilder: StrategyBuilder,
      //todo fix shares' currencies and issue with profit.values.sum
      tradingPolicy: Policy = fixedTradeCostPolicy(),
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
    def apply(assetData: AssetData): F[TradingStats] = Sync[F].defer {
      val series = assetData.barSeries
      val asset = assetData.asset
      val strategy = strategyBuilder(assetData)
      val lots: Function[Bar, Num] = bar => {
        series.numOf {
          tradingPolicy.apply(TradeRequest(asset, bar.getClosePrice.doubleValue, manualTrade = false)).lots
        }
      }
      val seriesManager = new BarSeriesManager(series, transactionCostModel, holdingCostModel)
      for {
        longRecord <- Sync[F].blocking(
          seriesManager.run(strategy.getLongStrategy, TradeType.BUY, lots)
        )
        shortRecord <- Sync[F].blocking(
          seriesManager.run(strategy.getShortStrategy, TradeType.SELL, lots)
        )
      } yield TradingStats(
        long = Stats.fromRecord(longRecord, series, asset, includeCurrent = false),
        short = Stats.fromRecord(shortRecord, series, asset, includeCurrent = false)
      )
    }
  }
}
