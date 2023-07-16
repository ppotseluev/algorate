package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.implicits._
import com.github.ppotseluev.algorate.BarInfo
import com.github.ppotseluev.algorate.ClosePositionOrder.StopType
import com.github.ppotseluev.algorate.EnrichedPosition
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Price
import com.github.ppotseluev.algorate.Stats
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.MoneyTracker
import com.github.ppotseluev.algorate.strategy.FullStrategy.TradeIdea
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.AssetsFilter
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.CandleData
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.TraderResponse.Payload
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.TraderSnapshotRequested
import com.github.ppotseluev.algorate.trader.feature.FeatureToggles
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.typesafe.scalalogging.LazyLogging
import scala.collection.parallel.CollectionConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object TradingManager extends LazyLogging {

  sealed trait Event
  object Event {
    case class CandleData(barInfo: BarInfo) extends Event
    case class TraderSnapshotRequested(instrumentId: InstrumentId) extends Event
    case class ExitRequested(instrumentId: InstrumentId) extends Event
    case class TraderSnapshotEvent(snapshot: Trader.StateSnapshot) extends Event
    case class TradeRequested(
        instrumentId: InstrumentId,
        operationType: TradeIdea
    ) extends Event
    case class SetStop(instrumentId: InstrumentId, stopType: StopType, value: Price) extends Event
    case class FindAssets(filter: AssetsFilter, callback: List[TradingAsset] => Unit) extends Event
    case class TraderResponse(asset: TradingAsset, payload: Payload) extends Event
    object TraderResponse {
      sealed trait Payload
      object Payload {
        case class Check(result: Boolean) extends Payload
      }
    }
  }

  def apply[F[_]](
      assets: Map[InstrumentId, TradingAsset],
      broker: Broker[Future],
      strategy: StrategyBuilder,
      moneyTracker: MoneyTracker[F],
      policy: Policy,
      keepLastBars: Int,
      eventsSink: EventsSink[Future],
      checkOrdersStatusEvery: FiniteDuration = 3.seconds,
      maxLag: Option[FiniteDuration],
      enableTrading: Boolean
  )(implicit featureToggles: FeatureToggles): Behavior[Event] = Behaviors.setup { ctx =>
    val ordersWatcher = ctx.spawn(
      OrdersWatcher(checkOrdersStatusEvery, broker),
      "orders-watcher"
    )

    def trader(instrumentId: InstrumentId): Behavior[Trader.Event] = {
      val asset = assets(instrumentId)
      Trader(
        asset = asset,
        strategyBuilder = strategy,
        policy = policy,
        broker = broker,
        keepLastBars = keepLastBars,
        ordersWatcher = ordersWatcher,
        snapshotSink = ctx.self,
        maxLag = maxLag,
        enableTrading = enableTrading
      )
    }

    val traders = assets.keys.map { instrumentId =>
      instrumentId -> ctx.spawn(trader(instrumentId), s"$instrumentId-trader")
    }.toMap

    def useTrader(instrumentId: InstrumentId)(f: Trader => Unit): Unit =
      traders.get(instrumentId) match {
        case Some(trader) => f(trader)
        case None         => logger.error(s"Trader for $instrumentId not found")
      }

    var longTrades = Set.empty[EnrichedPosition]
    var shortTrades = Set.empty[EnrichedPosition]

    def tradingStats() = TradingStats(
      long = Stats(longTrades.toSeq.sortBy(_.entryTime)),
      short = Stats(shortTrades.toSeq.sortBy(_.entryTime))
    )

    var foundAssets: List[TradingAsset] = Nil
    var waitingForTraders = 0
    var callback: List[TradingAsset] => Unit = _ => ()

    Behaviors.receiveMessage {
      case CandleData(data) =>
        logger.debug(s"Received $data")
        useTrader(data.instrumentId)(_ ! Trader.Event.NewData(data.bar))
        Behaviors.same
      case TraderSnapshotRequested(instrumentId) =>
        useTrader(instrumentId)(_ ! Trader.Event.StateSnapshotRequested)
        Behaviors.same
      case Event.TraderSnapshotEvent(snapshot) =>
        longTrades = longTrades ++ snapshot.tradingStats.long.enrichedPositions
        shortTrades = shortTrades ++ snapshot.tradingStats.short.enrichedPositions
        val event = com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot(
          snapshot,
          tradingStats(),
          moneyTracker.get.orEmpty
        )
        eventsSink.push(event) //TODO check future's result?
        Behaviors.same
      case Event.TradeRequested(instrumentId, operationType) =>
        useTrader(instrumentId)(_ ! Trader.Event.TradeRequested(operationType))
        Behaviors.same
      case Event.ExitRequested(instrumentId) =>
        useTrader(instrumentId)(_ ! Trader.Event.ExitRequested)
        Behaviors.same
      case Event.SetStop(instrumentId, stopType, value) =>
        useTrader(instrumentId)(_ ! Trader.Event.SetStop(stopType, value))
        Behaviors.same
      case Event.FindAssets(assetsFilter, cb) =>
        assetsFilter match {
          case traderFilter: AssetsFilter.TraderFilter =>
            foundAssets = Nil
            callback = cb
            waitingForTraders = traders.size
            traders.values.par.foreach { trader =>
              trader ! Trader.Event.Check(traderFilter, ctx.self)
            }
          case AssetsFilter.All =>
            cb(assets.values.toList)
        }
        Behaviors.same
      case Event.TraderResponse(asset, payload) =>
        waitingForTraders -= 1
        payload match {
          case Payload.Check(fits) =>
            if (fits) {
              foundAssets = asset :: foundAssets
            }
        }
        if (waitingForTraders == 0) {
          callback(foundAssets)
        }
        Behaviors.same
    }
  }
}
