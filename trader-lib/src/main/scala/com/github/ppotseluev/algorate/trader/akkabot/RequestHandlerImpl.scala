package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.ActorSystem
import cats.effect.kernel.Sync
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.trader.Request
import com.github.ppotseluev.algorate.trader.RequestHandler
import com.typesafe.scalalogging.LazyLogging

class RequestHandlerImpl[F[_]: Sync](
    actorSystem: ActorSystem[TradingManager.Event],
    shares: Map[Ticker, InstrumentId],
    eventsSink: EventsSink[F]
) extends RequestHandler[F]
    with LazyLogging {

  override def handle(request: Request): F[Unit] = Sync[F].delay {
    request match {
      case Request.ShowState(ticker) =>
        shares.get(ticker).foreach { instrumentId =>
          actorSystem ! TradingManager.Event.TraderSnapshotRequested(instrumentId)
        }
    }
  }
}
