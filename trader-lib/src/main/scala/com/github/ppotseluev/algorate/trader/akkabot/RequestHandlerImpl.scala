package com.github.ppotseluev.algorate.trader.akkabot

import cats.implicits._
import akka.actor.typed.ActorSystem
import cats.effect.kernel.Sync
import cats.effect.Ref
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.trader.Request
import com.github.ppotseluev.algorate.trader.RequestHandler
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State.{
  WaitingShowTicker,
  WaitingTradingTicker
}
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.{Message, MessageSource}
import com.typesafe.scalalogging.LazyLogging

class RequestHandlerImpl[F[_]: Sync](
    actorSystem: ActorSystem[TradingManager.Event],
    assets: Map[Ticker, InstrumentId],
    eventsSink: EventsSink[F],
    state: Ref[F, State]
) extends RequestHandler[F]
    with LazyLogging {

  private def notifyTraders(ticker: Ticker, event: InstrumentId => TradingManager.Event) =
    Sync[F].delay {
      assets.get(ticker).foreach { instrumentId =>
        actorSystem ! event(instrumentId)
      }
    }

  override def handle(request: Request, reply: MessageSource => F[Unit]): F[Unit] = Sync[F].defer {
    def requestTicker(newState: State) = reply(MessageSource("Enter the ticker")).flatMap { _ =>
      state.set(newState)
    }
    request match {
      case Request.ShowState => requestTicker(WaitingShowTicker)
      case Request.Sell      => requestTicker(WaitingTradingTicker(OperationType.Sell))
      case Request.Buy       => requestTicker(WaitingTradingTicker(OperationType.Sell))
      case Request.GeneralInput(input) =>
        val ticker = s"${input.toUpperCase}USDT" //TODO
        state.get.flatMap {
          case State.Empty => Sync[F].raiseError(new IllegalArgumentException("Unexpected input"))
          case WaitingTradingTicker(operation) =>
            notifyTraders(ticker, TradingManager.Event.TradeRequested(_, operation))
          case WaitingShowTicker =>
            notifyTraders(ticker, TradingManager.Event.TraderSnapshotRequested)
        }
      case Request.ShowActiveTrades => ???
    }
  }
}

object RequestHandlerImpl {
  sealed trait State
  object State {
    case object Empty extends State
    case class WaitingTradingTicker(operation: OperationType) extends State
    case object WaitingShowTicker extends State
  }
}
