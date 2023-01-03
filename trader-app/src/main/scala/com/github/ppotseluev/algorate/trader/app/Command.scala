package com.github.ppotseluev.algorate.trader.app

import akka.actor.typed.ActorSystem
import cats.effect.kernel.Sync
import cats.effect.std.Console
import cats.implicits._
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Ticker
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager

sealed trait Command

object Command {
  case class ShowState(ticker: Ticker) extends Command

  private val show = "show ([0-9a-zA-Z]+)".r

  def parse(input: String): Option[Command] = input match {
    case show(ticker) => ShowState(ticker).some
    case _            => None
  }
}

/**
 * Utils to handle user CLI commands
 */
object CommandHandler {
  def handleUserCommand[F[_]: Console: Sync](
      actorSystem: ActorSystem[TradingManager.Event],
      shares: Map[Ticker, InstrumentId]
  ): F[Unit] =
    for {
      input <- Console[F].readLine
      _ <- Sync[F].delay {
        Command.parse(input) match {
          case Some(Command.ShowState(ticker)) =>
            shares.get(ticker) match {
              case Some(id) =>
                actorSystem ! TradingManager.Event.ShowStateRequested(id)
              case None =>
                println(s"Can't find share with ticker $ticker")
            }
          case None => println("Incorrect command")
        }
      }
    } yield ()
}
