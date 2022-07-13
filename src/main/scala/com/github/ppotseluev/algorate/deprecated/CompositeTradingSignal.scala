//package com.github.ppotseluev.algorate.core
//
//import cats.data.NonEmptyList
//import com.github.ppotseluev.algorate.deprecated.TradingSignal.Decision
//
//class CompositeTradingSignal( //TODO is it needed?
//    signals: NonEmptyList[TradingSignal]
//) extends TradingSignal {
//
//  override def push(newPoints: NonEmptyList[Point]): Unit =
//    signals.toList.foreach(_.push(newPoints))
//
//  override def apply(): TradingSignal.Decision =
//    signals
//      .map(_.apply())
//      .collect { case trade: Decision.Trade =>
//        trade
//      }
//      .maxByOption(_.confidence)
//
//}
