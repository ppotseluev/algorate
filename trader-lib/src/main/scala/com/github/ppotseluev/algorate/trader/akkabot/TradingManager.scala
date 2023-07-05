package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.implicits._
import com.github.ppotseluev.algorate.{
  BarInfo,
  EnrichedPosition,
  InstrumentId,
  OperationType,
  Stats,
  TradingAsset,
  TradingStats
}
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.MoneyTracker
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.CandleData
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.TraderSnapshotRequested
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.typesafe.scalalogging.LazyLogging

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
    case class TradeRequested(instrumentId: InstrumentId, operationType: OperationType)
        extends Event
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
      maxLag: Option[FiniteDuration]
  ): Behavior[Event] = Behaviors.setup { ctx =>
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
        maxLag = maxLag
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
    }
  }
}
