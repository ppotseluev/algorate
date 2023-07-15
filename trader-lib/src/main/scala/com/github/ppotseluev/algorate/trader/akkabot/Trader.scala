package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import cats.implicits._
import com.github.ppotseluev.algorate.ClosePositionOrder.StopType
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Completed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Failed
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus.Pending
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.strategy.FullStrategy.TradeIdea
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.trader.LoggingSupport
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.AssetsFilter
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.AssetsFilter.TraderFilter
import com.github.ppotseluev.algorate.trader.akkabot.Trader.Event.OrderUpdated
import com.github.ppotseluev.algorate.trader.akkabot.Trader.Position.State
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.TraderResponse.Payload
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

import scala.collection.mutable
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
      unsafe: StateSnapshot.Unsafe,
      currentPrice: Price
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
    private[Trader] case class OrderPlaced(info: OrderPlacementInfo, callback: () => Unit)
        extends OrderPlacementUpdate
    private[Trader] case class FailedToPlaceOrder(
        order: Order,
        error: Throwable,
        callback: () => Unit
    ) extends Event

    case class NewData(bar: Bar) extends Event
    case object StateSnapshotRequested extends Event
    case class TradeRequested(trade: TradeIdea) extends Event
    case object ExitRequested extends Event
    case class OrderUpdated(info: OrderPlacementInfo) extends OrderPlacementUpdate
    case class SetStop(stopType: StopType, value: Price) extends Event
    case class Check(filter: TraderFilter, sender: TradingManager) extends Event
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
      maxLag: Option[FiniteDuration],
      enableTrading: Boolean
  )(implicit featureToggles: FeatureToggles): Behavior[Event] = {

    val logger = getLogger(s"Trader-${asset.ticker}")

    val tradingEnabled = featureToggles.register("trading-enabled", enableTrading)

    def buildOrder(
        point: Point,
        trade: TradeIdea,
        lots: Double
    ): Order = Order(
      asset = asset,
      lots = BigDecimal(lots).setScale(asset.quantityScale, RoundingMode.HALF_DOWN),
      operationType = trade.operationType,
      details = Order.Details.Market, //TODO
      info = Order.Info(point, closingOrderType = None),
      exitBounds = trade.exitBounds
    )

    def orderPlacedEvent(
        order: Order,
        onSuccess: () => Unit,
        onFailure: () => Unit
    )(result: Try[OrderPlacementInfo]): Event =
      result match {
        case Failure(exception) => Trader.Event.FailedToPlaceOrder(order, exception, onFailure)
        case Success(info)      => Trader.Event.OrderPlaced(info, onSuccess)
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

      val positionsStops = new mutable.HashMap[Int, ExitBounds]()

      def historyRecord(operationType: OperationType) = operationType match {
        case OperationType.Buy  => longHistory
        case OperationType.Sell => shortHistory
      }

      def placeOrder(
          order: Order,
          onSuccess: () => Unit,
          onFailure: () => Unit
      )(implicit ctx: ActorContext[Event]): Unit =
        ctx.pipeToSelf(broker.placeOrder(order))(orderPlacedEvent(order, onSuccess, onFailure))

      def tryEnter(bar: Bar, trade: TradeIdea)(implicit
          ctx: ActorContext[Event]
      ): Unit = {
        val lastIndex = barSeries.getEndIndex
        val point = Point(
          index = lastIndex,
          timestamp = bar.endTime,
          value = bar.closePrice
        )
        val tradeRequest = TradeRequest(
          asset = asset,
          price = point.value
        )
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        policy.apply(tradeRequest) match {
          case Decision.Allowed(lots) =>
            val order = buildOrder(point, trade, lots)
            val prevState = state
            state = TraderState.enter(order)
            placeOrder(
              order,
              onSuccess = () => {
                historyRecord(trade.operationType).enter(
                  lastIndex,
                  lastPrice,
                  barSeries.numOf(order.lots)
                )
                positionsStops(lastIndex) = trade.exitBounds
              },
              onFailure = () => {
                state = prevState
              }
            )
          case Decision.Denied(message) => logger.warn(message)
        }
      }

      def exit(
          bar: Bar,
          position: Position
      )(implicit ctx: ActorContext[Event]): Unit = {
        val lastIndex = barSeries.getEndIndex
        val history = historyRecord(position.payload.operationType)
        val point = Point(
          index = lastIndex,
          timestamp = bar.endTime,
          value = bar.closePrice
        )
        val lastPrice = barSeries.getBar(lastIndex).getClosePrice
        val order = position.payload.buildClosingOrder(point)
        val prevState = state
        state = TraderState.exit(order, position)
        placeOrder(
          order,
          onSuccess = () => {
            history.exit(lastIndex, lastPrice, barSeries.numOf(order.lots))
          },
          onFailure = () => {
            state = prevState
          }
        )
      }

      def shouldExit(bar: Bar, position: Order): Boolean =
        position.exitBounds.shouldExit(bar.closePrice)
//        position.operationType match {
//          case OperationType.Buy =>
//            strategy.getLongStrategy.shouldExit(barSeries.getEndIndex, longHistory)
//          case OperationType.Sell =>
//            strategy.getShortStrategy.shouldExit(barSeries.getEndIndex, shortHistory)
//        }

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
              strategy.recommendedTrade(lastIndex).foreach { trade =>
                if (tradingEnabled()) {
                  tryEnter(bar, trade)
                } else {
                  logger.warn("Trading disabled")
                }
              }
            case TraderState.Entering(_) => ()
            case _: TraderState.Exiting =>
              logger.info("Exiting position in progress")
          }
        } else {
          logger.debug(s"Lag is too big, not operating")
        }
      }

      def handleExit(bar: Bar)(implicit ctx: ActorContext[Event]): Unit =
        state match {
          case TraderState.Entering(position) =>
            position.state match {
              case State.Initial | State.Placed(Pending) | State.Placed(Failed) => ()
              case State.Placed(Completed) =>
                if (shouldExit(bar, position.payload)) {
                  exit(bar, position) //, postOrder = false) //exit by stop-signal
                } else {
                  () //keep holding current position
                }
            }
          case _ => ()
        }

      def handleBar(bar: Bar)(implicit ctx: ActorContext[Event]): Unit = {
        if (maxLag.forall(_ >= lag(bar))) {
          handleExit(bar)
        }
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
          short = Stats.fromRecord(shortHistory, barSeries, asset, includeCurrent = true),
          stopsInfo = positionsStops.toMap
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
          ),
          currentPrice = currentBar.fold(BigDecimal(0))(_.closePrice)
        )
      }

      def sinkSnapshot(event: Event): Unit = {
        val snapshot = buildSnapshot(event)
        snapshotSink ! TraderSnapshotEvent(snapshot)
      }

      def checkFilter(filter: TraderFilter): Boolean = filter match {
        case AssetsFilter.HasChannel =>
          strategy.channelIndicator.getValue(barSeries.getEndIndex).isDefined
      }

      Behaviors.receive { (ctx, event) =>
        implicit val context: ActorContext[Event] = ctx
        event match {
          case Trader.Event.NewData(bar) =>
            logger.debug(s"Received tick $bar")
            handleBar(bar)
          case Trader.Event.OrderPlaced(info, callback) =>
            callback()
            ordersWatcher ! OrdersWatcher.Request.RegisterOrder(info, ctx.self)
          case event: Trader.Event.OrderUpdated =>
            handleOrderInfo(event)
          case Trader.Event.FailedToPlaceOrder(order, t, callback) =>
            callback()
            logger.error(s"Failed to place order $order", t)
          case Trader.Event.StateSnapshotRequested =>
            sinkSnapshot(event)
          case Trader.Event.TradeRequested(trade) =>
            currentBar.foreach { bar =>
              tryEnter(bar, trade)
            }
          case Trader.Event.ExitRequested =>
            (currentBar, state) match {
              case (Some(bar), TraderState.Entering(position)) =>
                exit(bar, position) //, postOrder = true)
              case _ => logger.warn("Can't perform requested exit operation")
            }
          case Trader.Event.SetStop(stopType, value) =>
            state match {
              case TraderState.Entering(position) =>
                val order = position.payload
                val exitBounds = order.exitBounds
                val newExitBounds = stopType match {
                  case StopType.StopLoss   => exitBounds.copy(stopLoss = value)
                  case StopType.TakeProfit => exitBounds.copy(takeProfit = value)
                }
                val updatedPosition = position.copy(
                  payload = order.copy(exitBounds = newExitBounds)
                )
                val index = order.info.point.index
                positionsStops(index) = newExitBounds
                state = TraderState.Entering(updatedPosition)
              case _ => ()
            }
          case Trader.Event.Check(filter, sender) =>
            val response = TradingManager.Event.TraderResponse(
              asset,
              Payload.Check(checkFilter(filter))
            )
            sender ! response
        }
        Behaviors.same
      }
    }
  }
}
