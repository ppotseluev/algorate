package com.github.ppotseluev.algorate.core

import cats.effect.kernel.Async
import cats.syntax.option._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.core.TradingBot.Action
import com.github.ppotseluev.algorate.core.TradingSignal.Decision
import com.github.ppotseluev.algorate.model.Order.Details
import com.github.ppotseluev.algorate.model.{InstrumentId, Order, Price}
import fs2.Stream

class TradingBot[F[_]](
    instrumentId: InstrumentId,
    source: Stream[F, Point],
    signal: TradingSignal,
    orderLimit: Price
)(implicit F: Async[F]) {

  val run: Stream[F, TradingBot.Action] =
    source.evalMapFilter(handle)

  private def handle(point: Point): F[Option[TradingBot.Action]] =
    F.delay {
      signal.push(point)
      signal()
    }.map(buildAction(point.value))

  private def buildAction(currentPrice: Price)(decision: Decision): Option[TradingBot.Action] =
    decision match {
      case Decision.Trade(operationType, confidence, takeProfit, stopLoss) =>
        val lots: Int =
          (orderLimit * confidence / currentPrice).toInt //todo handle not enough money
        if (lots > 0) {
          val order = Order(
            instrumentId = instrumentId,
            lots = lots,
            operationType = operationType,
            details = Details.Market //todo support Limit
          )
          Action
            .PlaceOrder(
              order = order,
              takeProfit = Some(takeProfit),
              stopLoss = Some(stopLoss)
            )
            .some
        } else {
          None
        }
      case Decision.Wait =>
        None
    }
}

object TradingBot {
  sealed trait Action

  object Action {
    case class PlaceOrder(
        order: Order,
        takeProfit: Option[Price],
        stopLoss: Option[Price]
    ) extends Action
  }
}
