package com.github.ppotseluev.algorate.ta4j.test

import cats.Monoid
import cats.derived.semiauto
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.Stats
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.TradingStats
import org.ta4j.core.BarSeries
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.Position
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord
import scala.jdk.CollectionConverters._

case class StrategyTester(
    strategyBuilder: BarSeries => FullStrategy
) {
  def test(series: BarSeries): TradingStats = {
    val strategy = strategyBuilder(series)
    val seriesManager = new BarSeriesManager(series)
    val longRecord = seriesManager.run(strategy.longStrategy, TradeType.BUY)
    val shortRecord = seriesManager.run(strategy.shortStrategy, TradeType.SELL)
    TradingStats(
      long = Stats.fromRecord(longRecord),
      short = Stats.fromRecord(shortRecord)
    )
  }
}

object StrategyTester {
  case class TradingStats(
      long: Stats,
      short: Stats
  ) {
    override def toString: String =
      s"LONG (${long.totalClosedPositions}, ${long.winRatio}), SHORT (${short.totalClosedPositions}, ${short.winRatio})"
  }

  object TradingStats {
    implicit val Monoid: Monoid[TradingStats] = semiauto.monoid
  }

  case class Stats(
      totalClosedPositions: Int,
      winningPositions: Int,
      positions: Seq[Position]
  ) {
    def winRatio: Double = winningPositions.toDouble / totalClosedPositions
  }

  object Stats {
    implicit val Monoid: Monoid[Stats] = semiauto.monoid

    def fromRecord(record: TradingRecord): Stats = {
      val positions = record.getPositions.asScala.toSeq
      Stats(
        totalClosedPositions = positions.count(_.isClosed),
        winningPositions = positions.count(_.hasProfit),
        positions = positions
      )
    }
  }
}
