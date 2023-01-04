package com.github.ppotseluev.algorate.trader.akkabot

import com.github.ppotseluev.algorate.TradingStats

/**
 * Common internal event model
 */
sealed trait Event
object Event {
  case class TradingSnapshot(
      snapshot: Trader.StateSnapshot,
      aggregatedStats: TradingStats
  ) extends Event

  case class Failure(message: String) extends Event
}
