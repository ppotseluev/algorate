package com.github.ppotseluev.algorate.ta4j.test

import cats.Monoid
import cats.derived.semiauto
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.Stats
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.TradingStats
import java.time.Year
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.ta4j.core.BarSeries
import org.ta4j.core.BarSeriesManager
import org.ta4j.core.Position
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord
import scala.collection.immutable.SeqMap
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
      long = Stats.fromRecord(longRecord, series),
      short = Stats.fromRecord(shortRecord, series)
    )
  }
}

object StrategyTester {
  case class EnrichedPosition(
      position: Position,
      entryTime: ZonedDateTime
  )

  case class TradingStats(
      long: Stats,
      short: Stats
  ) {
    def monthly: SeqMap[YearMonth, TradingStats] = {
      val l = long.monthly
      val s = short.monthly
      val stats = (l.keySet ++ s.keySet).toSeq.sorted.map { month =>
        month -> TradingStats(
          long = l.getOrElse(month, Monoid[Stats].empty),
          short = s.getOrElse(month, Monoid[Stats].empty)
        )
      }
      SeqMap.from(stats)
    }

    override def toString: String =
      s"LONG (${long.totalClosedPositions}, ${long.winRatio}), SHORT (${short.totalClosedPositions}, ${short.winRatio})"
  }

  object TradingStats {
    implicit val Monoid: Monoid[TradingStats] = semiauto.monoid
  }

  case class Stats(enrichedPositions: Seq[EnrichedPosition]) {
    val positions = enrichedPositions.map(_.position)

    val totalClosedPositions: Int = positions.count(_.isClosed)
    val winningPositions: Int = positions.count(_.hasProfit)
    val winRatio: Double = winningPositions.toDouble / totalClosedPositions

    def forMonth(month: YearMonth): Stats = {
      val start = month.atEndOfMonth.minusMonths(1).atStartOfDay.toInstant(ZoneOffset.UTC)
      val end = month.atEndOfMonth.atStartOfDay.plusDays(1).toInstant(ZoneOffset.UTC)
      val monthPositions = enrichedPositions.filter { pos =>
        pos.entryTime.toInstant.isAfter(start) && pos.entryTime.toInstant.isBefore(end)
      }
      Stats(monthPositions)
    }

    def forYear(year: Year): Stats = {
      val startDateTime = year.atDay(1).atStartOfDay
      val start = startDateTime.toInstant(ZoneOffset.UTC)
      val end = startDateTime.plusDays(1).plusYears(1).toInstant(ZoneOffset.UTC)
      val yearPositions = enrichedPositions.filter { pos =>
        pos.entryTime.toInstant.isAfter(start) && pos.entryTime.toInstant.isBefore(end)
      }
      Stats(yearPositions)
    }

    def monthly: SeqMap[YearMonth, Stats] =
      if (enrichedPositions.isEmpty) {
        SeqMap.empty
      } else {
        val min = YearMonth.from(enrichedPositions.minBy(_.entryTime).entryTime)
        val max = YearMonth.from(enrichedPositions.maxBy(_.entryTime).entryTime)
        val monthlyStat = LazyList
          .iterate(min)(_.plusMonths(1))
          .takeWhile(!_.isAfter(max))
          .map(month => month -> forMonth(month))
        SeqMap.from(monthlyStat)
      }
  }

  object Stats {
    implicit val Monoid: Monoid[Stats] = new Monoid[Stats] {
      override val empty: Stats = Stats(Seq.empty)

      override def combine(x: Stats, y: Stats): Stats =
        Stats(x.enrichedPositions ++ y.enrichedPositions)
    }

    def fromRecord(record: TradingRecord, series: BarSeries): Stats = {
      val positions = record.getPositions.asScala.toSeq.map { pos =>
        val entryTime = series.getBar(pos.getEntry.getIndex).getBeginTime
        EnrichedPosition(pos, entryTime)
      }
      Stats(positions)
    }
  }
}
