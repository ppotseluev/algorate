package com.github.ppotseluev.algorate.core

import cats.effect.kernel.Async
import cats.syntax.option._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.core.TradingAnalyzer.Action
import com.github.ppotseluev.algorate.core.TradingSignal.Decision
import com.github.ppotseluev.algorate.model.Order.{Details, Info}
import com.github.ppotseluev.algorate.model.{InstrumentId, Order, Price}
import fs2.Stream

/**
 * Analyzes data for specific trading instruments and emits
 * stream of actions
 */
case class TradingAnalyzer[F[_]](
    instrumentId: InstrumentId,
    source: Stream[F, Point],
    signal: TradingSignal,
    orderLimit: Price
)(implicit F: Async[F]) {

  val run: Stream[F, TradingAnalyzer.Action] =
    source.evalMapFilter(handle)

  private def handle(point: Point): F[Option[TradingAnalyzer.Action]] =
    F.delay {
      signal.push(point)
      signal()
    }.map(buildAction(point.value))

  private def buildAction(currentPrice: Price)(decision: Decision): Option[TradingAnalyzer.Action] =
    decision match {
      case Decision.Trade(operationType, confidence, takeProfit, stopLoss) =>
        val lots: Int =
          (orderLimit * confidence / currentPrice).toInt //todo handle not enough money
        if (lots > 0) {
          val order = Order(
            instrumentId = instrumentId,
            lots = lots,
            operationType = operationType,
            details = Details.Market(currentPrice), //todo support Limit
            info = Info(None)
          )
          Action
            .PlaceOrder(
              order = order,
              takeProfit = takeProfit,
              stopLoss = stopLoss
            )
            .some
        } else {
          None
        }
      case Decision.Wait =>
        None
    }
}

object TradingAnalyzer {
  sealed trait Action

  object Action {
    case class PlaceOrder(
        order: Order,
        takeProfit: Price,
        stopLoss: Price
    ) extends Action
  }
}
