package com.github.ppotseluev.algorate.ta4j

import cats.Monoid
import cats.derived.semiauto
import com.github.ppotseluev.algorate.ta4j.StrategyTester.{Stats, TradingStats}
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.{BarSeries, BarSeriesManager, Position, TradingRecord}

import scala.jdk.CollectionConverters._

class StrategyTester(
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
      s"""
        |LONG: total = ${long.totalClosedPositions}, win = ${long.winningPositions.toDouble / long.totalClosedPositions}
        |SHORT: total = ${short.totalClosedPositions}, win = ${short.winningPositions.toDouble / short.totalClosedPositions}
        |""".stripMargin
  }

  object TradingStats {
    implicit val Monoid: Monoid[TradingStats] = semiauto.monoid
  }

  case class Stats(
      totalClosedPositions: Int,
      winningPositions: Int,
      positions: Seq[Position]
  )

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
