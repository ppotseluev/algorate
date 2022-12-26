package com.github.ppotseluev.algorate.trader

import cats.Monoid
import cats.derived.semiauto

import java.time.YearMonth
import scala.collection.immutable.SeqMap

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
