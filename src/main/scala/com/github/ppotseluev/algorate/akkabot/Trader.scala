package com.github.ppotseluev.algorate.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import com.github.ppotseluev.algorate.akkabot.Trader.Event.FailedToPlaceOrder
import com.github.ppotseluev.algorate.akkabot.Trader.Event.NewData
import com.github.ppotseluev.algorate.akkabot.Trader.Event.OrderPlaced
import com.github.ppotseluev.algorate.akkabot.Trader.Event.ShowStateRequested
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.OperationType
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Point
import com.github.ppotseluev.algorate.ta4j.Charts
import com.github.ppotseluev.algorate.ta4j.Utils
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.Stats
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.TradingStats
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Trader extends LazyLogging {
  sealed trait Event
  object Event {
    case class NewData(bar: Bar) extends Event
    case class OrderPlaced(orderId: OrderId, order: Order) extends Event
    case class FailedToPlaceOrder(error: Throwable) extends Event
    case object ShowStateRequested extends Event
  }

  def apply(
      instrumentId: InstrumentId,
      strategyBuilder: BarSeries => FullStrategy,
      broker: Broker[Future],
      keepLastBars: Int
  ): Behavior[Event] = {
    def buildOrder(point: Point, operationType: OperationType): Order = Order(
      instrumentId = instrumentId,
      lots = 1, //TODO
      operationType = operationType,
      details = Order.Details.Market, //TODO
      info = Order.Info(point, closingOrderType = None)
    )

    def brokerPlaceOrder(order: Order): Future[(OrderId, Order)] =
      broker.placeOrder(order).map(_ -> order)

    def orderPlacedEvent(result: Try[(OrderId, Order)]): Event =
      result match {
        case Failure(exception)        => FailedToPlaceOrder(exception)
        case Success((orderId, order)) => OrderPlaced(orderId, order)
      }

    Behaviors.setup { _ =>
      val barSeries = new BaseBarSeries(instrumentId)
      barSeries.setMaximumBarCount(keepLastBars)
      val strategy = strategyBuilder(barSeries)
      var currentBar: Option[Bar] = None
      var currentPosition: Option[Order] = None
      val longHistory = new BaseTradingRecord()
      val shortHistory = new BaseTradingRecord(TradeType.SELL)
      def historyRecord(operationType: OperationType) = operationType match {
        case OperationType.Buy  => longHistory
        case OperationType.Sell => shortHistory
      }

      def shouldExit(position: Order): (Boolean, TradingRecord) = position.operationType match {
        case OperationType.Buy =>
          strategy.longStrategy.shouldExit(barSeries.getEndIndex, longHistory) -> longHistory
        case OperationType.Sell =>
          strategy.shortStrategy.shouldExit(barSeries.getEndIndex, shortHistory) -> shortHistory
      }

      def handleClosedBar(bar: Bar, ctx: ActorContext[Event]): Unit = {
        val ta4jBar = Utils.convertBar(bar)
        barSeries.addBar(ta4jBar)
        val point = Point(
          timestamp = bar.endTime,
          value = bar.closePrice
        )
        val lastIndex = barSeries.getEndIndex
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        def placeOrder(order: Order): Unit =
          ctx.pipeToSelf(brokerPlaceOrder(order))(orderPlacedEvent)
        def enter(operationType: OperationType): Unit = {
          val order = buildOrder(point, operationType)
          currentPosition = Some(order)
          historyRecord(operationType).enter(lastIndex, lastPrice, barSeries.numOf(order.lots))
          placeOrder(order)
        }
        currentPosition match {
          case Some(position) =>
            val (exit, historyRecord) = shouldExit(position)
            if (exit) {
              currentPosition = None
              val order = position.buildClosingOrder(point)
              historyRecord.exit(lastIndex, lastPrice, barSeries.numOf(order.lots))
              placeOrder(order)
            } else ()
          case None =>
            if (strategy.longStrategy.shouldEnter(lastIndex)) {
              enter(OperationType.Buy)
            } else if (strategy.shortStrategy.shouldEnter(lastIndex)) {
              enter(OperationType.Sell)
            } else ()
        }
      }

      //todo stop-loss & take-profit, check if there is no memory leak caused by indicators caching

      Behaviors.receive { (ctx, event) =>
        event match {
          case NewData(bar) =>
            currentBar match {
              case None =>
                currentBar = Some(bar)
              case Some(cur) =>
                if (bar.endTime.isEqual(cur.endTime)) {
                  // received update for the current bar
                  currentBar = Some(bar)
                } else if (bar.endTime.isAfter(cur.endTime)) {
                  // received data for the new bar, consider current bar as closed
                  handleClosedBar(cur, ctx)
                  currentBar = Some(bar)
                } else { // bar.time < curBar.time
                  // received an update for some old bar. Let's ignore it for now
                  logger.warn(s"Received old data, ts = ${bar.endTime}, curTs = ${cur.endTime}")
                }
            }
          case OrderPlaced(orderId, order) =>
            order.info.closingOrderType match {
              case Some(value) => logger.info(s"Close position ($value)")
              case None        => logger.info(s"Placed order $orderId, point ${order.info.point}")
            }
          case FailedToPlaceOrder(t) =>
            logger.error(s"Failed to place order", t)
          case ShowStateRequested =>
            val tradingStats = TradingStats(
              long = Stats.fromRecord(longHistory, barSeries),
              short = Stats.fromRecord(shortHistory, barSeries)
            )
            logger.info(s"$instrumentId $tradingStats")
            Charts.display(
              strategyBuilder = strategyBuilder,
              series = barSeries,
              tradingStats = Some(tradingStats),
              title = instrumentId
            )
        }
        Behaviors.same
      }
    }
  }
}
