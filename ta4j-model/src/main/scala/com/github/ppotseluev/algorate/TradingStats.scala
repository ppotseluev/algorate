package com.github.ppotseluev.algorate

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.implicits._
import java.time.YearMonth
import scala.collection.immutable.SeqMap

case class TradingStats(
    long: Stats,
    short: Stats
) {
  def totalPositions: Int = long.totalClosedPositions + short.totalClosedPositions

  def totalWinningPositions(fee: Boolean = false): Int =
    long.winningPositions(fee) + short.winningPositions(fee)
  def totalNonWinningPositions(fee: Boolean = false): Int =
    long.nonWinningPositions(fee) + short.nonWinningPositions(fee)

  def totalWinRatio(fee: Boolean): Double = {
    val totalWon = long.winningPositions(fee) + short.winningPositions(fee)
    totalWon.toDouble / totalPositions
  }

  def profit(
      fee: Boolean,
      profitable: Option[Boolean] = None
  ): Map[Currency, Double] =
    (long.enrichedPositions ++ short.enrichedPositions)
      .groupBy(_.asset.currency)
      .view
      .mapValues(_.map(_.position))
      .mapValues { positions =>
        if (fee) {
          val p =
            if (profitable.contains(true)) positions.filter(_.hasProfit)
            else if (profitable.contains(false)) positions.filterNot(_.hasProfit)
            else positions
          p.foldMap(_.getProfit.doubleValue)
        } else {
          val p =
            if (profitable.contains(true)) positions.filter(_.getGrossProfit.isPositive)
            else if (profitable.contains(false)) positions.filterNot(_.getGrossProfit.isPositive)
            else positions
          p.foldMap(_.getGrossProfit.doubleValue)
        }
      }
      .toMap

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

  override def toString: String = Show[TradingStats].show(this)
}

object TradingStats {
  implicit val monoid: Monoid[TradingStats] = semiauto.monoid
  implicit val show: Show[TradingStats] = Show.show { s =>
    import s._
    val totalNoFee = totalWinRatio(false)
    val totalReal = totalWinRatio(true)
    val diff = (totalNoFee - totalReal) / totalNoFee * 100
    val noFeeProfit = profit(fee = false, profitable = true.some)
    val avgProfit = noFeeProfit.view.mapValues(_ / totalWinningPositions()).toMap
    val avgLoss = profit(fee = false, profitable = false.some).view
      .mapValues(_ / totalNonWinningPositions())
      .toMap
    val profitReport =
      s"NO_FEE_PROFIT: ${profit(fee = false)}, REAL_PROFIT: ${profit(fee = true)}, NO_FEE_ONLY_PROFITABLE: $noFeeProfit"
    s"""
       |LONG (${long.totalClosedPositions}, no_fee ${long.winRatio(false)}, real ${long.winRatio(
      true
    )}),
       |SHORT (${short.totalClosedPositions}, no_fee ${short.winRatio(false)}, real ${short
      .winRatio(true)}),
       |SUM ($totalPositions, no_fee $totalNoFee, real $totalReal),
       |$profitReport
       |AVG_PROFIT: $avgProfit, AVG_LOSS: $avgLoss
       |DIFF: $diff%
       |""".stripMargin //.replaceAll("\n", "")
  }
}
