package com.github.ppotseluev.algorate.trader.cli

import cats.effect.Sync
import cats.effect.std.Console
import cats.implicits._
import com.github.ppotseluev.algorate.trader.telegram.{BotToken, TelegramClient}
import com.github.ppotseluev.algorate.trader.{Request, RequestHandler}

/**
 * Utils to handle user CLI commands
 */
class AlgorateCli[F[_]: Console: Sync](
    requestHandler: RequestHandler[F],
    telegramClient: TelegramClient[F],
    chatId: String,
    botToken: BotToken
) {
  val run: F[Unit] =
    for {
      input <- Console[F].readLine
      request = Request.fromString(input)
      _ <- requestHandler.handle(request, m => telegramClient.send(botToken)(m.toMessage(chatId)))
    } yield ()
}
