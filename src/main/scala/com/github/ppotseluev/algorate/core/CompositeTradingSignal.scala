package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.core.TradingSignal.Decision

class CompositeTradingSignal( //TODO is it needed?
    signals: Seq[TradingSignal]
) extends TradingSignal {

  override def push(newPoints: Seq[Point]): Unit =
    signals.foreach(_.push(newPoints))

  override def apply(): TradingSignal.Decision =
    signals
      .map(_.apply())
      .collect { case trade: Decision.Trade =>
        trade
      }
      .maxByOption(_.confidence)
      .getOrElse(Decision.Wait)

}
