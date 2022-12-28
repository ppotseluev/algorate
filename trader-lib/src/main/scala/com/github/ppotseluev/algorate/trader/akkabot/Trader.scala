package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import cats.implicits._
import com.github.ppotseluev.algorate.BarsConverter
import com.github.ppotseluev.algorate.Stats
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Completed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Failed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Pending
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.trader.akkabot.Trader.Position.State
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Trader extends LazyLogging {
  sealed trait Event
  object Event {
    private[Trader] sealed trait OrderPlacementUpdate extends Event {
      def info: OrderPlacementInfo
    }
    private[Trader] case class OrderPlaced(info: OrderPlacementInfo) extends OrderPlacementUpdate
    private[Trader] case class FailedToPlaceOrder(error: Throwable) extends Event

    case class NewData(bar: Bar) extends Event
    case object ShowStateRequested extends Event
    case class OrderUpdated(info: OrderPlacementInfo) extends OrderPlacementUpdate
  }

  sealed trait TraderState
  object TraderState {
    def enter(order: Order) = Entering(Position.initial(order))
    def exit(order: Order, originalPosition: Position) =
      Exiting(Position.initial(order), originalPosition)

    case object Empty extends TraderState
    sealed trait Trading extends TraderState {
      def position: Position
    }
    case class Entering(position: Position) extends Trading
    case class Exiting(position: Position, originalPosition: Position) extends Trading
  }

  case class Position(
      payload: Order,
      placementInfo: Option[OrderPlacementInfo]
  ) {
    def state: Position.State =
      placementInfo
        .map(_.status)
        .fold(Position.State.initial)(Position.State.Placed)
  }

  object Position {
    sealed trait State
    object State {
      def initial: State = Initial
      case object Initial extends State
      case class Placed(status: OrderExecutionStatus) extends State
    }
    def initial(order: Order) = Position(order, placementInfo = None)
  }

  def apply(
      instrumentId: InstrumentId,
      strategyBuilder: BarSeries => FullStrategy,
      broker: Broker[Future],
      keepLastBars: Int,
      ordersWatcher: OrdersWatcher
  ): Behavior[Event] = {
    def buildOrder(point: Point, operationType: OperationType): Order = Order(
      instrumentId = instrumentId,
      lots = 1, //TODO
      operationType = operationType,
      details = Order.Details.Market, //TODO
      info = Order.Info(point, closingOrderType = None)
    )

    def orderPlacedEvent(result: Try[OrderPlacementInfo]): Event =
      result match {
        case Failure(exception) => Trader.Event.FailedToPlaceOrder(exception)
        case Success(info)      => Trader.Event.OrderPlaced(info)
      }

    Behaviors.setup { _ =>
      val barSeries = new BaseBarSeries(instrumentId)
      barSeries.setMaximumBarCount(keepLastBars)
      val strategy = strategyBuilder(barSeries)
      var currentBar: Option[Bar] = None
      var state: TraderState = TraderState.Empty
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
        val ta4jBar = BarsConverter.convertBar(bar)
        barSeries.addBar(ta4jBar)
        val point = Point(
          timestamp = bar.endTime,
          value = bar.closePrice
        )
        val lastIndex = barSeries.getEndIndex
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        def placeOrder(order: Order): Unit =
          ctx.pipeToSelf(broker.placeOrder(order))(orderPlacedEvent)
        def enter(operationType: OperationType): Unit = {
          val order = buildOrder(point, operationType)
          state = TraderState.enter(order)
          historyRecord(operationType).enter(lastIndex, lastPrice, barSeries.numOf(order.lots))
          placeOrder(order)
        }
        state match {
          case TraderState.Empty =>
            if (strategy.longStrategy.shouldEnter(lastIndex)) {
              enter(OperationType.Buy)
            } else if (strategy.shortStrategy.shouldEnter(lastIndex)) {
              enter(OperationType.Sell)
            } else ()
          case TraderState.Entering(position) =>
            position.state match {
              case State.Initial | State.Placed(Pending) | State.Placed(Failed) => ()
              case State.Placed(Completed) =>
                val (exit, historyRecord) = shouldExit(position.payload)
                if (exit) {
                  val order = position.payload.buildClosingOrder(point)
                  state = TraderState.exit(order, position)
                  historyRecord.exit(lastIndex, lastPrice, barSeries.numOf(order.lots))
                  placeOrder(order)
                } else {
                  () //keep holding current position
                }
            }
          case _: TraderState.Exiting =>
            logger.info("Exiting position in progress")
        }
      }

      def handleBar(bar: Bar, ctx: ActorContext[Event]): Unit =
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

      def visualizeState(): Unit = {
        val tradingStats = TradingStats(
          long = Stats.fromRecord(longHistory, barSeries),
          short = Stats.fromRecord(shortHistory, barSeries)
        )
        logger.info(s"$instrumentId $tradingStats")
        TradingCharts.display(
          strategyBuilder = strategyBuilder,
          series = barSeries,
          tradingStats = Some(tradingStats),
          title = instrumentId
        )
      }

      def handleOrderInfo(placementInfo: OrderPlacementInfo): Unit =
        state match {
          case TraderState.Empty =>
            logger.error(
              s"Illegal state, received placement info $placementInfo while in Empty state"
            )
          case TraderState.Entering(position) =>
            require(
              position.placementInfo.forall(_.orderId == placementInfo.orderId),
              s"Received unexpected order status $placementInfo"
            )
            val updatedPosition = position.copy(placementInfo = placementInfo.some)
            updatedPosition.state match {
              case State.Placed(Failed) =>
                logger.error("Failed to enter")
                state = TraderState.Empty
              case _ =>
                state = TraderState.Entering(updatedPosition)
            }
          case exiting @ TraderState.Exiting(pos, _) =>
            require(
              pos.placementInfo.forall(_.orderId == placementInfo.orderId),
              s"Received unexpected order status $placementInfo"
            )
            val position = pos.copy(placementInfo = placementInfo.some)
            position.state match {
              case State.Initial | State.Placed(Pending) =>
                state = exiting.copy(position = position)
              case State.Placed(Completed) =>
                logger.info("Position closed")
                state = TraderState.Empty
              case State.Placed(Failed) =>
                logger.error("CRITICAL ALERT: Failed to exit position, manual action required")
                state = exiting.copy(position = position)
            }
        }

      //todo stop-loss & take-profit, check if there is no memory leak caused by indicators caching

      Behaviors.receive { (ctx, event) =>
        event match {
          case Trader.Event.NewData(bar) => handleBar(bar, ctx)
          case Trader.Event.OrderPlaced(info) =>
            ordersWatcher ! OrdersWatcher.Request.RegisterOrder(info, ctx.self)
          case Trader.Event.OrderUpdated(info)    => handleOrderInfo(info)
          case Trader.Event.FailedToPlaceOrder(t) => logger.error(s"Failed to place order", t)
          case Trader.Event.ShowStateRequested    => visualizeState()
        }
        Behaviors.same
      }
    }
  }
}
