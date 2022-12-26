package com.github.ppotseluev.algorate.tools.strategy

import com.github.ppotseluev.algorate.strategy.ta4j.FullStrategy
import com.github.ppotseluev.algorate.trader.Stats
import com.github.ppotseluev.algorate.trader.TradingStats
import org.ta4j.core.BarSeries
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.Trade.TradeType

private[strategy] case class StrategyTester(
    strategyBuilder: BarSeries => FullStrategy
) {
  def test(series: BarSeries): TradingStats = {
    val strategy = strategyBuilder(series)
    val seriesManager = new BarSeriesManager(series)
    val longRecord = seriesManager.run(strategy.longStrategy, TradeType.BUY)
    val shortRecord = seriesManager.run(strategy.shortStrategy, TradeType.SELL)
    TradingStats(
      long = Stats.fromRecord(longRecord, series),
      short = Stats.fromRecord(shortRecord, series)
    )
  }
}
