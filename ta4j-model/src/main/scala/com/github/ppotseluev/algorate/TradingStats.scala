package com.github.ppotseluev.algorate

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.implicits._
import java.time.YearMonth
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import scala.collection.immutable.SeqMap

case class TradingStats(
    long: Stats,
    short: Stats
) {
  lazy val noFeeProfit = profit(fee = false, profitable = true.some)
  lazy val noFeeLoss = profit(fee = false, profitable = false.some)
  def profitRatio(fee: Boolean) =
    profit(fee, profitable = true.some).alignMergeWith(
      profit(fee, profitable = false.some)
    )((x, y) => scala.math.abs(x / y))

  def isEmpty: Boolean = totalPositions == 0

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
    val avgProfit = noFeeProfit.view.mapValues(_ / totalWinningPositions()).toMap
    val avgLoss = noFeeLoss.view.mapValues(_ / totalNonWinningPositions()).toMap
    val positions = long.closedPositions ++ short.closedPositions
    val durations = positions
      .map { p =>
        p.getExit.getIndex - p.getEntry.getIndex
      }
      .map(_.toDouble)
    val avgDuration = durations.sum / positions.size
    val maxDuration = durations.maxOption.getOrElse(0)
    val p = new Percentile(0.8)
    val p80 = p.evaluate(durations.toArray)
    s"""
       |LONG (${long.totalClosedPositions}, no_fee ${long.winRatio(false)}, real ${long.winRatio(
      true
    )}),
       |SHORT (${short.totalClosedPositions}, no_fee ${short.winRatio(false)}, real ${short
      .winRatio(true)}),
       |SUM ($totalPositions, no_fee $totalNoFee, real $totalReal),
       |NET_PROFIT: ${profit(fee = false)} / ${profit(fee = true)}, PROFIT_RATIO: ${profitRatio(
      false
    )} / ${profitRatio(true)}
       |ONLY_PROFITABLE: $noFeeProfit, ONLY_LOSS: $noFeeLoss
       |AVG_PROFIT: $avgProfit, AVG_LOSS: $avgLoss
       |AVG_DURATION: $avgDuration [candles], MAX_DURATION: $maxDuration, P_80: $p80
       |DIFF: $diff%
       |""".stripMargin //.replaceAll("\n", "")
  }
}
