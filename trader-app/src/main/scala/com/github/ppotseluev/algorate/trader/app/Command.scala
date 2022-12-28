package com.github.ppotseluev.algorate.trader.app

import akka.actor.typed.ActorSystem
import cats.effect.kernel.Sync
import cats.effect.std.Console
import cats.implicits._
import com.github.ppotseluev.algorate.Ticker
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager
import ru.tinkoff.piapi.contract.v1.Share

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
      shares: List[Share]
  ): F[Unit] = {
    val sharesMap = shares.groupMapReduce(_.getTicker)(identity) { (s1, s2) =>
      throw new IllegalArgumentException(
        s"Two shares ${s1.getFigi}, ${s2.getFigi} with the same ticker ${s1.getTicker}"
      )
    }
    for {
      input <- Console[F].readLine
      _ <- Sync[F].delay {
        Command.parse(input) match {
          case Some(Command.ShowState(ticker)) =>
            sharesMap.get(ticker) match {
              case Some(share) =>
                actorSystem ! TradingManager.Event.ShowStateRequested(share.getFigi)
              case None =>
                println(s"Can't find share with ticker $ticker")
            }
          case None => println("Incorrect command")
        }
      }
    } yield ()
  }
}
