package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.implicits._
import cats.kernel.Monoid
import com.github.ppotseluev.algorate.broker.{Broker, MoneyTracker}
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager.Event.{CandleData, TraderSnapshotRequested}
import com.github.ppotseluev.algorate.trader.policy.Policy
import com.github.ppotseluev.algorate.{BarInfo, InstrumentId, TradingAsset, TradingStats}
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeries

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

object TradingManager extends LazyLogging {

  sealed trait Event
  object Event {
    case class CandleData(barInfo: BarInfo) extends Event
    case class TraderSnapshotRequested(instrumentId: InstrumentId) extends Event
    case class TraderSnapshotEvent(snapshot: Trader.StateSnapshot) extends Event
  }

  def apply[F[_]](
      assets: Map[InstrumentId, TradingAsset],
      broker: Broker[Future],
      strategy: BarSeries => FullStrategy,
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
        instrumentId = instrumentId,
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

    var tradingStats = Monoid[TradingStats].empty

    Behaviors.receiveMessage {
      case CandleData(data) =>
        logger.debug(s"Received $data")
        useTrader(data.instrumentId)(_ ! Trader.Event.NewData(data.bar))
        Behaviors.same
      case TraderSnapshotRequested(instrumentId) =>
        useTrader(instrumentId)(_ ! Trader.Event.StateSnapshotRequested)
        Behaviors.same
      case Event.TraderSnapshotEvent(snapshot) =>
        tradingStats = tradingStats |+| snapshot.tradingStats
        val event = com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot(
          snapshot,
          tradingStats,
          moneyTracker.get.orEmpty
        )
        eventsSink.push(event) //TODO check future's result?
        Behaviors.same
    }
  }
}
