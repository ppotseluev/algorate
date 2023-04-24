package com.github.ppotseluev.algorate

import cats.Monoid
import java.time.Year
import java.time.YearMonth
import java.time.ZoneOffset
import org.ta4j.core.BarSeries
import org.ta4j.core.TradingRecord
import scala.collection.immutable.SeqMap
import scala.jdk.CollectionConverters._

case class Stats(enrichedPositions: Seq[EnrichedPosition]) {
  val positions = enrichedPositions.map(_.position)

  val totalClosedPositions: Int = positions.count(_.isClosed)
  def winningPositions(fee: Boolean): Int = positions.count { p =>
    if (fee) p.hasProfit
    else p.getGrossProfit.isPositive
  }

  def nonWinningPositions(fee: Boolean): Int = positions.count { p =>
    if (fee) !p.hasProfit
    else !p.getGrossProfit.isPositive
  }

  def winRatio(fee: Boolean): Double = winningPositions(fee).toDouble / totalClosedPositions

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
      Stats(
        (x.enrichedPositions ++ y.enrichedPositions)
          .distinctBy(_.entryTime)
          .sortBy(_.position.getEntry.getIndex)
      )
  }

  def fromRecord(
      record: TradingRecord,
      series: BarSeries,
      asset: TradingAsset
  ): Stats = {
    val positions = record.getPositions.asScala.toSeq.map { pos =>
      val entryTime = series.getBar(pos.getEntry.getIndex).getBeginTime
      EnrichedPosition(pos, entryTime, asset)
    }
    Stats(positions)
  }
}
