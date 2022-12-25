//package com.github.ppotseluev.algorate.deprecated
//
//import cats.effect.Async
//import cats.instances.seq._
//import cats.syntax.applicative._
//import cats.syntax.flatMap._
//import cats.syntax.functor._
//import cats.syntax.option._
//import cats.syntax.traverse._
//import com.github.ppotseluev.algorate.core.Broker
//import com.github.ppotseluev.algorate.deprecated.TradingBot.Action
//import com.github.ppotseluev.algorate.deprecated.TradingBot.PositionInfo
//import com.github.ppotseluev.algorate.deprecated.TradingSignal.Decision
//import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
//import com.github.ppotseluev.algorate.model.Order.Details
//import com.github.ppotseluev.algorate.model.Order.Info
//import com.github.ppotseluev.algorate.model._
//import com.typesafe.scalalogging.LazyLogging
//import fs2.Stream
//import scala.collection.concurrent.TrieMap
//import scala.collection.mutable
//
//class TradingBot[F[_]](
//    instrumentId: InstrumentId,
//    source: Stream[F, Point],
//    signal: TradingSignal,
////    orderLimit: Price,
//    broker: Broker[F]
//)(implicit F: Async[F])
//    extends LazyLogging {
//
//  private val positions: mutable.Map[OrderId, PositionInfo] = TrieMap.empty
//
//  val run: Stream[F, PlacedOrder] = source
//    .evalMap(handle)
//    .evalMap(execute)
//    .flatMap(Stream.emits)
//
//  private def execute(actions: Seq[Action[F]]): F[Seq[PlacedOrder]] = {
//    def exec(action: Action[F]): F[PlacedOrder] = broker
//      .placeOrder(action.order)
//      .map(PlacedOrder(_, action.order))
//      .flatMap(placedOrder => action.afterPlacement(placedOrder).as(placedOrder))
//    actions.traverse(exec)
//  }
//
//  private def clearPosition(orderId: OrderId): F[Unit] = F.delay {
//    positions.remove(orderId)
//  }
//
//  private def handle(point: Point): F[Seq[Action[F]]] = {
//    val closingActions = positions.toSeq.flatMap { case (orderId, pos) =>
//      Some(pos.stopLoss)
//        .filter(_.isFired(point.value))
//        .orElse(
//          Some(pos.takeProfit).filter(_.isFired(point.value))
//        )
//        .map(_.buildMarketOrder(point))
//        .map(Action(_, afterPlacement = _ => clearPosition(orderId)))
//    }
//    F.delay {
//      signal.push(point)
//      signal(point)
//    }.flatMap(buildAction(point))
//      .map(tradeAction => closingActions ++ tradeAction)
//  }
//
//  private def buildAction(
//      point: Point
//  )(decision: Decision): F[Option[Action[F]]] = //todo refactor
//    decision match {
//      case Decision.Trade(operationType, confidence, takeProfit, stopLoss, _) =>
//        val lots = 1
////        lazy val lots: Int = FIXME
////          (orderLimit * confidence / currentPrice).toInt //todo handle not enough money
//        if (confidence > 0.8 && lots > 0) { // todo parametrize this 0.6
//
//          val order = Order(
//            instrumentId = instrumentId,
//            lots = lots,
//            operationType = operationType,
//            details = Details.Market, //todo support Limit
//            info = Info(point, None)
//          )
//          val registerPosition: PlacedOrder => F[Unit] = placedOrder =>
//            F.delay {
//              positions += placedOrder.id -> PositionInfo(
//                originalOrder = order,
//                originalOrderId = placedOrder.id,
//                stopLoss = ClosePositionOrder(order, stopLoss, Type.StopLoss),
//                takeProfit = ClosePositionOrder(order, takeProfit, Type.TakeProfit)
//              )
//            }
//          Action(order, afterPlacement = registerPosition).some.pure[F]
//        } else {
//          None.pure[F].widen
//        }
//    }
//
////  def closePosition(currentPoint: Point): F[Unit] = { //TODO remove?
////    for {
////      pos <- OptionT.fromOption(position)
////      order = Order(
////        instrumentId = pos.originalOrder.instrumentId,
////        lots = pos.originalOrder.lots,
////        operationType = pos.originalOrder.operationType.reverse,
////        details = Details.Market,
////        info = Info(currentPoint, None)
////      )
////      _ <- OptionT.liftF(broker.placeOrder(order))
////    } yield ()
////  }.value.void
//}
//
//object TradingBot {
//  case class PositionInfo(
//      originalOrder: Order,
//      originalOrderId: OrderId,
//      stopLoss: ClosePositionOrder,
//      takeProfit: ClosePositionOrder
//  )
//
//  case class Action[F[_]](order: Order, afterPlacement: PlacedOrder => F[Unit])
//}
