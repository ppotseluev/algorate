package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import cats.implicits._
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Completed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Failed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Pending
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.trader.LoggingSupport
import com.github.ppotseluev.algorate.trader.akkabot.Trader.Event.OrderUpdated
import com.github.ppotseluev.algorate.trader.akkabot.Trader.Position.State
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.TraderSnapshotEvent
import com.github.ppotseluev.algorate.trader.feature.FeatureToggles
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest
import io.prometheus.client.Gauge

import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeries
import org.ta4j.core.BaseTradingRecord
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.TradingRecord
import org.ta4j.core.cost.ZeroCostModel
import org.ta4j.core.num.Num

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Trader extends LoggingSupport {
  private val feeModel = new ZeroCostModel()
//    new LinearTransactionCostModel(0.0005) TODO
  private val zeroCost = new ZeroCostModel()

  private def gauge(name: String) =
    Gauge
      .build()
      .namespace("algorate")
      .name(name)
      .help(name)
      .labelNames("ticker", "type")
      .register()

  val traderGauge = gauge("trader_data")
//  val priceMetric = gauge("price")
//  val timeMetric = gauge("time")

  case class StateSnapshot(
      asset: TradingAsset,
      triggeredBy: Event,
      strategyBuilder: StrategyBuilder,
      state: TraderState,
      firstBarTs: Option[ZonedDateTime],
      lastBar: Option[Bar],
      lag: Option[FiniteDuration],
      tradingStats: TradingStats,
      unsafe: StateSnapshot.Unsafe
  ) {
    def unsafeAssetData = AssetData(asset, unsafe.barSeries)
  }

  object StateSnapshot {
    case class Unsafe(
        barSeries: BarSeries,
        longHistory: TradingRecord,
        shortHistory: TradingRecord
    )
  }

  sealed trait Event
  object Event {
    private[Trader] sealed trait OrderPlacementUpdate extends Event {
      def info: OrderPlacementInfo
    }
    private[Trader] case class OrderPlaced(info: OrderPlacementInfo) extends OrderPlacementUpdate
    private[Trader] case class FailedToPlaceOrder(order: Order, error: Throwable) extends Event

    case class NewData(bar: Bar) extends Event
    case object StateSnapshotRequested extends Event
    case class TradeRequested(operationType: OperationType) extends Event
    case object ExitRequested extends Event
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
      asset: TradingAsset,
      strategyBuilder: StrategyBuilder,
      policy: Policy,
      broker: Broker[Future],
      keepLastBars: Int,
      ordersWatcher: OrdersWatcher,
      snapshotSink: TraderSnapshotSink,
      maxLag: Option[FiniteDuration]
  )(implicit featureToggles: FeatureToggles): Behavior[Event] = {

    val logger = getLogger(s"Trader-${asset.ticker}")

    val tradingEnabled = featureToggles.register("trading-enabled", true)

    def buildOrder(
        point: Point,
        operationType: OperationType,
        lots: Double
    ): Order = Order(
      asset = asset,
      lots = BigDecimal(lots).setScale(asset.quantityScale, RoundingMode.HALF_DOWN),
      operationType = operationType,
      details = Order.Details.Market, //TODO
      info = Order.Info(point, closingOrderType = None)
    )

    def orderPlacedEvent(order: Order)(result: Try[OrderPlacementInfo]): Event =
      result match {
        case Failure(exception) => Trader.Event.FailedToPlaceOrder(order, exception)
        case Success(info)      => Trader.Event.OrderPlaced(info)
      }

    Behaviors.setup { _ =>
      val barSeries = new BaseBarSeries(asset.instrumentId)
      barSeries.setMaximumBarCount(keepLastBars)
      val assetData = AssetData(asset, barSeries)
      val strategy = strategyBuilder(assetData)
      var currentBar: Option[Bar] = None
      var state: TraderState = TraderState.Empty
      val longHistory = new BaseTradingRecord(TradeType.BUY, feeModel, zeroCost)
      val shortHistory = new BaseTradingRecord(TradeType.SELL, feeModel, zeroCost)

      def historyRecord(operationType: OperationType) = operationType match {
        case OperationType.Buy  => longHistory
        case OperationType.Sell => shortHistory
      }

      def placeOrder(order: Order)(implicit ctx: ActorContext[Event]): Unit =
        ctx.pipeToSelf(broker.placeOrder(order))(orderPlacedEvent(order))

      def tryEnter(bar: Bar, operationType: OperationType)(implicit
          ctx: ActorContext[Event]
      ): Unit = {
        val point = Point(
          timestamp = bar.endTime,
          value = bar.closePrice
        )
        val trade = TradeRequest(
          asset = asset,
          price = point.value
        )
        val lastIndex = barSeries.getEndIndex
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        policy.apply(trade) match {
          case Decision.Allowed(lots) =>
            val order = buildOrder(point, operationType, lots)
            state = TraderState.enter(order)
            historyRecord(operationType).enter(lastIndex, lastPrice, barSeries.numOf(order.lots))
            placeOrder(order)
          case Decision.Denied(message) => logger.warn(message)
        }
      }

      def exit(
          bar: Bar,
          position: Position
      )(implicit ctx: ActorContext[Event]): Unit = {
        val history = historyRecord(position.payload.operationType)
        val point = Point(
          timestamp = bar.endTime,
          value = bar.closePrice
        )
        val lastIndex = barSeries.getEndIndex
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        val order = position.payload.buildClosingOrder(point)
        state = TraderState.exit(order, position)
        history.exit(lastIndex, lastPrice, barSeries.numOf(order.lots))
        placeOrder(order)
      }

      def shouldExit(position: Order): Boolean = position.operationType match {
        case OperationType.Buy =>
          strategy.longStrategy.shouldExit(barSeries.getEndIndex, longHistory)
        case OperationType.Sell =>
          strategy.shortStrategy.shouldExit(barSeries.getEndIndex, shortHistory)
      }

      def lag(bar: Bar): FiniteDuration =
        (OffsetDateTime.now.toEpochSecond - bar.endTime.toEpochSecond).seconds

      def handleClosedBar(bar: Bar)(implicit ctx: ActorContext[Event]): Unit = {
        traderGauge.labels(asset.ticker, "price").set(bar.closePrice.doubleValue)
        traderGauge.labels(asset.ticker, "time").set(bar.endTime.toEpochSecond.toDouble)

        val ta4jBar = BarsConverter.convertBar(bar)
        barSeries.addBar(ta4jBar)
        val lastIndex = barSeries.getEndIndex
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        assert(lastPrice.doubleValue == bar.closePrice, "wrong last price") //TODO
        if (maxLag.forall(_ >= lag(bar))) {
          state match {
            case TraderState.Empty =>
              if (!tradingEnabled()) {
                logger.warn("Trading disabled")
              } else if (strategy.longStrategy.shouldEnter(lastIndex)) {
                tryEnter(bar, OperationType.Buy)
              } else if (strategy.shortStrategy.shouldEnter(lastIndex)) {
                tryEnter(bar, OperationType.Sell)
              }
            case TraderState.Entering(position) =>
              position.state match {
                case State.Initial | State.Placed(Pending) | State.Placed(Failed) => ()
                case State.Placed(Completed) =>
                  if (shouldExit(position.payload)) {
                    exit(bar, position)
                  } else {
                    () //keep holding current position
                  }
              }
            case _: TraderState.Exiting =>
              logger.info("Exiting position in progress")
          }
        } else {
          logger.debug(s"Lag is too big, not operating")
        }
      }

      def handleBar(bar: Bar)(implicit ctx: ActorContext[Event]): Unit =
        currentBar match {
          case None =>
            currentBar = Some(bar)
          case Some(cur) =>
            if (bar.endTime.isEqual(cur.endTime)) {
              // received update for the current bar
              currentBar = Some(bar)
            } else if (bar.endTime.isAfter(cur.endTime)) {
              // received data for the new bar, consider current bar as closed
              handleClosedBar(cur)
              currentBar = Some(bar)
            } else { // bar.time < curBar.time
              // received an update for some old bar. Let's ignore it for now
              logger.warn(s"Received old data, ts = ${bar.endTime}, curTs = ${cur.endTime}")
            }
        }

      def handleOrderInfo(event: OrderUpdated): Unit = {
        val placementInfo = event.info
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
                logger.info(s"Entering order update: $placementInfo")
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
        sinkSnapshot(event)
      }

      def buildSnapshot(event: Event) = {
        val tradingStats = TradingStats(
          long = Stats.fromRecord(longHistory, barSeries, asset, includeCurrent = true),
          short = Stats.fromRecord(shortHistory, barSeries, asset, includeCurrent = true)
        )
        StateSnapshot(
          asset = asset,
          triggeredBy = event,
          strategyBuilder = strategyBuilder,
          state = state,
          firstBarTs = Option.unless(barSeries.isEmpty)(barSeries.getFirstBar).map(_.getEndTime),
          lastBar = currentBar,
          lag = currentBar.map(lag),
          tradingStats = tradingStats,
          unsafe = StateSnapshot.Unsafe(
            barSeries,
            longHistory = longHistory,
            shortHistory = shortHistory
          )
        )
      }

      def sinkSnapshot(event: Event): Unit = {
        val snapshot = buildSnapshot(event)
        snapshotSink ! TraderSnapshotEvent(snapshot)
      }

      Behaviors.receive { (ctx, event) =>
        implicit val context: ActorContext[Event] = ctx
        event match {
          case Trader.Event.NewData(bar) =>
            logger.debug(s"Received tick $bar")
            handleBar(bar)
          case Trader.Event.OrderPlaced(info) =>
            ordersWatcher ! OrdersWatcher.Request.RegisterOrder(info, ctx.self)
          case event: Trader.Event.OrderUpdated =>
            handleOrderInfo(event)
          case Trader.Event.FailedToPlaceOrder(order, t) =>
            logger.error(s"Failed to place order $order", t)
          case Trader.Event.StateSnapshotRequested =>
            sinkSnapshot(event)
          case Trader.Event.TradeRequested(operationType) =>
            currentBar.foreach { bar =>
              tryEnter(bar, operationType)
            }
          case Trader.Event.ExitRequested =>
            (currentBar, state) match {
              case (Some(bar), TraderState.Entering(position)) =>
                exit(bar, position)
              case _ => logger.warn("Can't perform requested exit operation")
            }
        }
        Behaviors.same
      }
    }
  }
}
