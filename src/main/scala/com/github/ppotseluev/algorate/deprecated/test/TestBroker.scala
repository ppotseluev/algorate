package com.github.ppotseluev.algorate.deprecated.test

import cats.effect.Async
import com.github.ppotseluev.algorate.core.Bar
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
import com.github.ppotseluev.algorate.model._
import com.softwaremill.tagging._

import java.util.UUID
import ru.tinkoff.piapi.contract.v1.Share

import scala.collection.concurrent.TrieMap
import TestBroker.TradingStatistics
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval

/**
 * Implementation for strategy testing
 */
class TestBroker[F[_]](realBroker: Broker[F])(implicit F: Async[F]) extends Broker[F] {

  private val journal = TrieMap.empty[InstrumentId, List[Order]]

  override def placeOrder(order: Order): F[OrderId] = F.delay {
    journal.updateWith(order.instrumentId) {
      case Some(value) => Some(order :: value)
      case None        => Some(List(order))
    }
    UUID.randomUUID().toString.taggedWith[Tags.OrderId]
  }

  override def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[Seq[Bar]] =
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
//      order.operationType match {
//        case OperationType.Buy =>
//          if (state.positionLots >= 0) //докупаем
//            newState.copy(
//              positionLots = state.positionLots + order.lots,
//              balanceDelta = state.balanceDelta - order.estimatedCost
//            )
//          else if (state.positionLots == -order.lots) //закрываем короткую позицию
//            newState.copy(
//              positionLots = 0,
//              balanceDelta = state.balanceDelta - order.estimatedCost
//            )
//          else ???
//        case OperationType.Sell =>
//          if (state.positionLots == order.lots) //закрываем длинную позицию
//            newState.copy(
//              positionLots = 0,
//              balanceDelta = state.balanceDelta + order.estimatedCost
//            )
//          else if (state.positionLots <= 0) //увеличиваем шорт
//            newState.copy(
//              positionLots = state.positionLots - order.lots,
//              balanceDelta = state.balanceDelta + order.estimatedCost
//            )
//          else ???
//      }
    }
  }

  override def getShare(ticker: Ticker): F[Share] = realBroker.getShare(ticker)

  override def getAllShares: F[List[Share]] = realBroker.getAllShares
}

object TestBroker {
  case class TradingInfo(
      orders: Seq[Order],
      currentLots: Int
  )

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
