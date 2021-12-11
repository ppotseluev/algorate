package com.github.ppotseluev.algorate.test

import cats.effect.Async
import com.github.ppotseluev.algorate.core.{Broker, Point}
import com.github.ppotseluev.algorate.model.{InstrumentId, OperationType, Order, OrderId, Tags}
import com.github.ppotseluev.algorate.test.TestBroker.TradingStatistics
import com.github.ppotseluev.algorate.util.Interval
import com.softwaremill.tagging._

import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.concurrent.TrieMap

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
      interval: Option[Interval[OffsetDateTime]]
  ): F[fs2.Stream[F, Point]] =
    realBroker.getData(instrumentId, interval)

  def getStatistics(instrumentId: InstrumentId): TradingStatistics = {
    val orders: List[Order] = journal.getOrElse(instrumentId, List.empty)
    orders.foldRight(TradingStatistics.Empty) { case (order, state) =>
      val newState = state.copy(ordersHistory = state.ordersHistory :+ order)
      order.operationType match {
        case OperationType.Buy =>
          if (state.positionLots >= 0) //докупаем
            newState.copy(
              positionLots = state.positionLots + order.lots,
              balanceDelta = state.balanceDelta - order.estimatedCost
            )
          else if (state.positionLots == -order.lots) //закрываем короткую позицию
            newState.copy(
              positionLots = 0,
              balanceDelta = state.balanceDelta + order.estimatedCost
            )
          else ???
        case OperationType.Sell =>
          if (state.positionLots == order.lots) //закрываем длинную позицию
            newState.copy(
              positionLots = 0,
              balanceDelta = state.balanceDelta + order.estimatedCost
            )
          else if (state.positionLots <= 0) //увеличиваем шорт
            newState.copy(
              positionLots = state.positionLots - order.lots,
              balanceDelta = state.balanceDelta - order.estimatedCost
            )
          else ???
      }
    }
  }
}

object TestBroker {
  case class TradingInfo(
      orders: Seq[Order],
      currentLots: Int
  )

  case class TradingStatistics(
      ordersHistory: Seq[Order],
      positionLots: Int,
      balanceDelta: Double
  )

  object TradingStatistics {
    val Empty: TradingStatistics = TradingStatistics(
      ordersHistory = Seq.empty,
      positionLots = 0,
      balanceDelta = 0
    )
  }

}
