package com.github.ppotseluev.algorate.core

import cats.data.OptionT
import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.option._
import com.github.ppotseluev.algorate.core.TradingAnalyzer.Action
import com.github.ppotseluev.algorate.core.TradingBot.PositionInfo
import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
import com.github.ppotseluev.algorate.model.Order.{Details, Info}
import com.github.ppotseluev.algorate.model._
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream

class TradingBot[F[_]](
    instrumentId: InstrumentId,
    source: Stream[F, Point],
    signal: TradingSignal,
    orderLimit: Price,
    broker: Broker[F]
)(implicit F: Async[F])
    extends LazyLogging {

  @volatile private var position: Option[PositionInfo] = None

  private val preparedSource: Stream[F, Point] = source.evalMap { point =>
    handle(point).as(point)
  }

  private def handle(point: Point): F[Unit] =
    position
      .flatMap { pos =>
        Some(pos.stopLoss)
          .filter(_.isFired(point.value))
          .orElse(
            Some(pos.takeProfit).filter(_.isFired(point.value))
          )
      }
      .map(_.buildMarketOrder(point.value))
      .fold(F.unit) {
        broker.placeOrder(_).map(_ => position = None)
      }

  private val analyzer = new TradingAnalyzer(
    instrumentId = instrumentId,
    source = preparedSource,
    signal = signal,
    orderLimit = orderLimit
  )

  val run: F[Unit] = analyzer.run.evalMap(execute).compile.drain

  private def execute(action: Action): F[Unit] = action match {
    case Action.PlaceOrder(order, takeProfit, stopLoss) =>
      if (position.isDefined) F.delay {
        logger.warn(
          s"Already have opened position for ${analyzer.instrumentId}. Ignore PlaceOrder action"
        )
      }
      else
        broker
          .placeOrder(order)
          .map { orderId =>
            position = PositionInfo(
              originalOrder = order,
              originalOrderId = orderId,
              stopLoss = ClosePositionOrder(order, stopLoss, Type.StopLoss),
              takeProfit = ClosePositionOrder(order, takeProfit, Type.TakeProfit)
            ).some
          }
          .void
  }

  def closePosition(currentPrice: Price): F[Unit] = {
    for {
      pos <- OptionT.fromOption(position)
      order = Order(
        instrumentId = pos.originalOrder.instrumentId,
        lots = pos.originalOrder.lots,
        operationType = pos.originalOrder.operationType.reverse,
        details = Details.Market(currentPrice),
        info = Info(None)
      )
      _ <- OptionT.liftF(broker.placeOrder(order))
    } yield ()
  }.value.void
}

object TradingBot {
  case class PositionInfo(
      originalOrder: Order,
      originalOrderId: OrderId,
      stopLoss: ClosePositionOrder,
      takeProfit: ClosePositionOrder
  )
}
