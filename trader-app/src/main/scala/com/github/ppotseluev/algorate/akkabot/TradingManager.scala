package com.github.ppotseluev.algorate.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.ppotseluev.algorate.akkabot.TradingManager.Event.CandleData
import com.github.ppotseluev.algorate.akkabot.TradingManager.Event.ShowStateRequested
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.model.BarInfo
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.strategy.ta4j.FullStrategy
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeries
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object TradingManager extends LazyLogging {

  sealed trait Event
  object Event {
    case class CandleData(barInfo: BarInfo) extends Event
    case class ShowStateRequested(instrumentId: InstrumentId) extends Event
  }

  def apply(
      tradingInstruments: Set[InstrumentId],
      broker: Broker[Future],
      strategy: BarSeries => FullStrategy,
      keepLastBars: Int,
      checkOrdersStatusEvery: FiniteDuration = 3.seconds
  ): Behavior[Event] = Behaviors.setup { ctx =>
    val ordersWatcher = ctx.spawn(
      OrdersWatcher(checkOrdersStatusEvery, broker),
      "orders-watcher"
    )

    def trader(instrumentId: InstrumentId): Behavior[Trader.Event] =
      Trader(
        instrumentId = instrumentId,
        strategyBuilder = strategy,
        broker = broker,
        keepLastBars = keepLastBars,
        ordersWatcher = ordersWatcher
      )
    val traders = tradingInstruments.map { instrumentId =>
      instrumentId -> ctx.spawn(trader(instrumentId), s"$instrumentId-trader")
    }.toMap

    def useTrader(instrumentId: InstrumentId)(f: Trader => Unit): Unit =
      traders.get(instrumentId) match {
        case Some(trader) => f(trader)
        case None         => logger.error(s"Trader for $instrumentId not found")
      }

    Behaviors.receiveMessage {
      case CandleData(data) =>
        logger.debug(s"Received $data")
        useTrader(data.instrumentId)(_ ! Trader.Event.NewData(data.bar))
        Behaviors.same
      case ShowStateRequested(instrumentId) =>
        useTrader(instrumentId)(_ ! Trader.Event.ShowStateRequested)
        Behaviors.same
    }
  }
}
