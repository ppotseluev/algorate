package com.github.ppotseluev.algorate.akkabot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.ppotseluev.algorate.akkabot.TradingManager.Event.CandleData
import com.github.ppotseluev.algorate.akkabot.TradingManager.Event.ShowStateRequested
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.model.BarInfo
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy
import com.typesafe.scalalogging.LazyLogging
import org.ta4j.core.BarSeries
import scala.concurrent.Future

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
      keepLastBars: Int
  ): Behavior[Event] = Behaviors.setup { ctx =>
    def trader(instrumentId: InstrumentId): Behavior[Trader.Event] =
      Trader(
        instrumentId = instrumentId,
        strategyBuilder = strategy,
        broker = broker,
        keepLastBars = keepLastBars
      )
    val traders = tradingInstruments.map { instrumentId =>
      instrumentId -> ctx.spawn(trader(instrumentId), s"$instrumentId-trader")
    }.toMap

    Behaviors.receiveMessage {
      case CandleData(data) =>
        logger.debug(s"Received $data")
        traders.get(data.instrumentId).foreach { trader =>
          trader ! Trader.Event.NewData(data.bar)
        }
        Behaviors.same
      case ShowStateRequested(instrumentId) =>
        traders.get(instrumentId).foreach { trader =>
          trader ! Trader.Event.ShowStateRequested
        }
        Behaviors.same
    }
  }
}
