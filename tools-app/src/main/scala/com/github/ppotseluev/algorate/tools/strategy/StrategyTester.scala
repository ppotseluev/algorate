package com.github.ppotseluev.algorate.tools.strategy

import cats.Monoid
import com.github.ppotseluev.algorate.Stats
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeries
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.Trade.TradeType

private[strategy] case class StrategyTester(
    strategyBuilder: BarSeries => FullStrategy,
    tradingPolicy: Policy = TestSetup.fixedTradeCostPolicy()
) extends LazyLogging {
  def test(series: BarSeries, asset: TradingAsset): TradingStats = {
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
      val longRecord = seriesManager.run(strategy.longStrategy, TradeType.BUY, lots)
      val shortRecord = seriesManager.run(strategy.shortStrategy, TradeType.SELL, lots)
      TradingStats(
        long = Stats.fromRecord(longRecord, series, asset),
        short = Stats.fromRecord(shortRecord, series, asset)
      )
    } else {
      logger.info(s"Skipping ${asset.ticker} ($avgPrice ${asset.currency})")
      Monoid[TradingStats].empty
    }
  }
}
