package com.github.ppotseluev.algorate.core

import cats.effect.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.core.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.core.TestBroker.TradingStatistics
import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
import com.github.ppotseluev.algorate.model._
import java.util.UUID
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.Share
import scala.collection.concurrent.TrieMap

/**
 * Implementation for strategy testing
 */
class TestBroker[F[_]: Sync] private (realBroker: Broker[F]) extends Broker[F] {

  private val journal = TrieMap.empty[InstrumentId, List[Order]]

  override def placeOrder(order: Order): F[OrderPlacementInfo] = Sync[F].delay {
    journal.updateWith(order.instrumentId) {
      case Some(value) => Some(order :: value)
      case None        => Some(List(order))
    }
    OrderPlacementInfo(
      orderId = UUID.randomUUID().toString,
      status = OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL
    )
  }

  override def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[List[Bar]] =
    realBroker.getData(instrumentId, interval)

  def getStatistics(
      instrumentId: InstrumentId,
      includeOpenedPosition: Boolean
  ): TradingStatistics = {
    val orders: List[Order] = {
      val orderList = journal.getOrElse(instrumentId, List.empty)
      if (!includeOpenedPosition && orderList.size % 2 != 0)
        orderList.tail
      else
        orderList
    }
    orders.foldRight(TradingStatistics.Empty) { case (order, state) =>
      val ordersHistory = state.ordersHistory :+ order
      val newState = state.copy(
        triggerCount = state.triggerCount.updatedWith(order.operationType) { oldValue =>
          val delta = if (order.isClosing) 0 else 1
          val newValue = oldValue.getOrElse(0) + delta
          Some(newValue)
        },
        successCount =
          state.successCount + (if (order.info.closingOrderType.contains(Type.TakeProfit)) 1
                                else 0),
        failureCount =
          state.failureCount + (if (order.info.closingOrderType.contains(Type.StopLoss)) 1 else 0),
        ordersHistory = ordersHistory
      )
      newState
    }
  }

  override def getAllShares: F[List[Share]] = realBroker.getAllShares

  override def getOrderState(orderId: OrderId): F[OrderState] =
    OrderState.newBuilder
      .setExecutionReportStatus(OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL)
      .build
      .pure[F]
}

object TestBroker {
  def wrap[F[_]: Sync](broker: Broker[F]) = new TestBroker(broker)

  case class TradingStatistics(
      triggerCount: Map[OperationType, Int],
      successCount: Int,
      failureCount: Int,
      ordersHistory: Seq[Order],
      positionLots: Int,
      balanceDelta: Double
  ) {
    val totalTriggerCount: Int = triggerCount.values.sum

    def summary: String = {
      s"""
        |TradingStatistics {
        | triggerCount = $totalTriggerCount (${triggerCount(OperationType.Buy)} buy, ${triggerCount(
        OperationType.Sell
      )} sell),
        | successCount = $successCount,
        | failureCount = $failureCount,
        | positionLots = $positionLots,
        | balanceDelta = $balanceDelta
        |}
        |""".stripMargin
    }
  }

  object TradingStatistics {
    val Empty: TradingStatistics = TradingStatistics(
      triggerCount = Map.empty.withDefault(_ => 0),
      successCount = 0,
      failureCount = 0,
      ordersHistory = Seq.empty,
      positionLots = 0,
      balanceDelta = 0
    )
  }

}
