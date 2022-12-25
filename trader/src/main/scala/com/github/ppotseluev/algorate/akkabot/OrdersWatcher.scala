package com.github.ppotseluev.algorate.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.ppotseluev.algorate.akkabot.OrdersWatcher.Request.CheckOrder
import com.github.ppotseluev.algorate.akkabot.OrdersWatcher.Request.OrderStatusUpdate
import com.github.ppotseluev.algorate.akkabot.OrdersWatcher.Request.RegisterOrder
import com.github.ppotseluev.algorate.broker.Broker
import Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.model.OrderId
import com.typesafe.scalalogging.LazyLogging
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus._
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
      status: OrderExecutionReportStatus,
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
            if (
              status == EXECUTION_REPORT_STATUS_NEW || status == EXECUTION_REPORT_STATUS_PARTIALLYFILL
            ) {
              orders = orders.updated(orderId, OrderData(status, trader))
              ctx.scheduleOnce(checkEvery, ctx.self, CheckOrder(orderId))
            } else {
              logger.warn("Received order in final status, returning received status")
              trader ! Trader.Event.OrderUpdated(info)
            }
          case CheckOrder(orderId) =>
            ctx.pipeToSelf(broker.getOrderState(orderId)) {
              case Failure(t) =>
                logger.error("Fail to get order status, retrying", t)
                CheckOrder(orderId)
              case Success(orderState) =>
                OrderStatusUpdate(OrderPlacementInfo(orderId, orderState.getExecutionReportStatus))
            }
          case OrderStatusUpdate(info @ OrderPlacementInfo(orderId, actualStatus)) =>
            val OrderData(status, trader) = orders(orderId)
            if (actualStatus == status) {
              ctx.scheduleOnce(checkEvery, ctx.self, CheckOrder(orderId))
            } else {
              trader ! Trader.Event.OrderUpdated(info)
              actualStatus match {
                case EXECUTION_REPORT_STATUS_FILL | EXECUTION_REPORT_STATUS_REJECTED |
                    EXECUTION_REPORT_STATUS_CANCELLED =>
                  orders = orders.removed(orderId)
                case EXECUTION_REPORT_STATUS_NEW | EXECUTION_REPORT_STATUS_PARTIALLYFILL =>
                  orders = orders.updated(orderId, OrderData(actualStatus, trader))
                  ctx.scheduleOnce(checkEvery, ctx.self, CheckOrder(orderId))
                case s @ (EXECUTION_REPORT_STATUS_UNSPECIFIED | UNRECOGNIZED) =>
                  throw new IllegalStateException(s"Wrong order status $s")
              }
            }
        }
        Behaviors.same
      }
    }
}
