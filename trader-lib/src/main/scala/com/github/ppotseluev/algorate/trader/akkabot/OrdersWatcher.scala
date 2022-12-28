package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.ppotseluev.algorate.OrderId
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Completed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Failed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Pending
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.trader.akkabot.OrdersWatcher.Request.CheckOrder
import com.github.ppotseluev.algorate.trader.akkabot.OrdersWatcher.Request.OrderStatusUpdate
import com.github.ppotseluev.algorate.trader.akkabot.OrdersWatcher.Request.RegisterOrder
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

object OrdersWatcher extends LazyLogging {

  sealed trait Request
  object Request {
    case class RegisterOrder(placementInfo: OrderPlacementInfo, trader: Trader) extends Request
    private[OrdersWatcher] case class CheckOrder(orderId: OrderId) extends Request
    private[OrdersWatcher] case class OrderStatusUpdate(placementInfo: OrderPlacementInfo)
        extends Request
  }

  case class OrderData(
      status: OrderExecutionStatus,
      trader: Trader
  )

  def apply(
      checkEvery: FiniteDuration,
      broker: Broker[Future]
  ): Behavior[Request] =
    Behaviors.setup { _ =>
      var orders: Map[OrderId, OrderData] = Map.empty

      Behaviors.receive { (ctx, request) =>
        request match {
          case RegisterOrder(info @ OrderPlacementInfo(orderId, status), trader) =>
            if (status == Pending) {
              orders = orders.updated(orderId, OrderData(status, trader))
              ctx.scheduleOnce(checkEvery, ctx.self, CheckOrder(orderId))
            } else {
              logger.warn("Received order in final status, returning received status")
              trader ! Trader.Event.OrderUpdated(info)
            }
          case CheckOrder(orderId) =>
            ctx.pipeToSelf(broker.getOrderInfo(orderId)) {
              case Failure(t) =>
                logger.error("Fail to get order status, retrying", t)
                CheckOrder(orderId)
              case Success(info) => OrderStatusUpdate(info)
            }
          case OrderStatusUpdate(info @ OrderPlacementInfo(orderId, actualStatus)) =>
            val OrderData(status, trader) = orders(orderId)
            if (actualStatus == status) {
              ctx.scheduleOnce(checkEvery, ctx.self, CheckOrder(orderId))
            } else {
              trader ! Trader.Event.OrderUpdated(info)
              actualStatus match {
                case Completed | Failed =>
                  orders = orders.removed(orderId)
                case Pending =>
                  orders = orders.updated(orderId, OrderData(actualStatus, trader))
                  ctx.scheduleOnce(checkEvery, ctx.self, CheckOrder(orderId))
              }
            }
        }
        Behaviors.same
      }
    }
}
