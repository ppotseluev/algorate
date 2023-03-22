package com.github.ppotseluev.algorate.trader.akkabot

import com.github.ppotseluev.algorate.{Currency, Money, TradingStats}

/**
 * Common internal event model
 */
sealed trait Event
object Event {
  case class TradingSnapshot(
      snapshot: Trader.StateSnapshot,
      aggregatedStats: TradingStats,
      money: Money
  ) extends Event
}
