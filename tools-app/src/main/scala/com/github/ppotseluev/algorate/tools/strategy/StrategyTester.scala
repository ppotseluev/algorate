package com.github.ppotseluev.algorate.tools.strategy

import cats.Id
import com.github.ppotseluev.algorate.cats.Provider
import com.github.ppotseluev.algorate.{
  Currency,
  Money,
  Stats,
  TradingAsset,
  TradingStats
}
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.trader.policy.Policy.{Decision, TradeRequest}
import com.github.ppotseluev.algorate.trader.policy.{MoneyManagementPolicy, Policy}
import org.ta4j.core.BarSeries
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.cost.{CostModel, LinearTransactionCostModel, ZeroCostModel}

private[strategy] case class StrategyTester(
    strategyBuilder: BarSeries => FullStrategy,
    tradingPolicy: Policy = StrategyTester.oneLotPolicy, //StrategyTester.fixedTradeCostPolicy()
) {
  def test(series: BarSeries, asset: TradingAsset): TradingStats = {
    val strategy = strategyBuilder(series)
    val avgPrice =
      series.getFirstBar.getClosePrice
        .plus(series.getLastBar.getClosePrice)
        .dividedBy(series.numOf(2))
    val lots = series.numOf {
      tradingPolicy.apply(TradeRequest(avgPrice.doubleValue, asset.currency)).lots
    }
    val seriesManager = new BarSeriesManager(series)
    val longRecord = seriesManager.run(strategy.longStrategy, TradeType.BUY, lots)
    val shortRecord = seriesManager.run(strategy.shortStrategy, TradeType.SELL, lots)
    TradingStats(
      long = Stats.fromRecord(longRecord, series, asset),
      short = Stats.fromRecord(shortRecord, series, asset)
    )
  }
}

private[strategy] object StrategyTester {
  val oneLotPolicy: Policy = _ => Decision.Allowed(1)

  def fixedTradeCostPolicy(
      usdTrade: Int = 200,
      rubTrade: Int = 15000
  ): Policy = {
    val money: Money = Map("usd" -> Int.MaxValue, "rub" -> Int.MaxValue)
    new MoneyManagementPolicy(() => Some(money))(
      maxPercentage = 1,
      maxAbsolute = Map(
        "usd" -> usdTrade,
        "rub" -> rubTrade
      )
    ).andThen(_.allowedOrElse(Decision.Allowed(1)))
  }
}
