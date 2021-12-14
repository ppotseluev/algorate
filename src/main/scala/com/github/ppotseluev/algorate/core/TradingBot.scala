package com.github.ppotseluev.algorate.core

import cats.data.OptionT
import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.syntax.applicative._
import com.github.ppotseluev.algorate.core.TradingBot.{Action, PositionInfo}
import com.github.ppotseluev.algorate.core.TradingSignal.Decision
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

  val run: Stream[F, PlacedOrder] = source
    .evalMapFilter(handle)
    .evalMap(execute)

  private def execute(action: Action[F]): F[PlacedOrder] =
    broker
      .placeOrder(action.order)
      .map(PlacedOrder(_, action.order))
      .flatMap(placedOrder => action.afterPlacement(placedOrder).as(placedOrder))

  private val clearPosition: F[Unit] = F.delay { position = None }

  private def handle(point: Point): F[Option[Action[F]]] =
    position match {
      case Some(pos) =>
        Some(pos.stopLoss)
          .filter(_.isFired(point.value))
          .orElse(
            Some(pos.takeProfit).filter(_.isFired(point.value))
          )
          .map(_.buildMarketOrder(point.value))
          .map(Action(_, afterPlacement = _ => clearPosition))
          .pure
      case None =>
        F.delay {
          signal.push(point)
          signal()
        }.flatMap(buildAction(point.value))
    }

  private def buildAction(
      currentPrice: Price
  )(decision: Decision): F[Option[Action[F]]] = //todo refactor
    decision match {
      case Decision.Trade(operationType, confidence, takeProfit, stopLoss) =>
        val lots: Int =
          (orderLimit * confidence / currentPrice).toInt //todo handle not enough money
        if (lots > 0) {
          if (position.isDefined) {
            F.delay {
              logger.warn(
                s"Already have opened position for $instrumentId. Ignore PlaceOrder action"
              )
            }.as(None)
          } else {
            val order = Order(
              instrumentId = instrumentId,
              lots = lots,
              operationType = operationType,
              details = Details.Market(currentPrice), //todo support Limit
              info = Info(None)
            )
            val registerPosition: PlacedOrder => F[Unit] = placedOrder =>
              F.delay {
                position = PositionInfo(
                  originalOrder = order,
                  originalOrderId = placedOrder.id,
                  stopLoss = ClosePositionOrder(order, stopLoss, Type.StopLoss),
                  takeProfit = ClosePositionOrder(order, takeProfit, Type.TakeProfit)
                ).some
              }
            Action(order, afterPlacement = registerPosition).some.pure
          }
        } else {
          None.pure.widen
        }
      case Decision.Wait =>
        None.pure.widen
    }

  def closePosition(currentPrice: Price): F[Unit] = { //TODO remove?
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

  case class Action[F[_]](order: Order, afterPlacement: PlacedOrder => F[Unit])
}
