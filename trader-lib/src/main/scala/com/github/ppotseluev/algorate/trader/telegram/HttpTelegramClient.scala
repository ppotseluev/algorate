package com.github.ppotseluev.algorate.trader.telegram

import cats.MonadError
import cats.implicits._
import com.github.ppotseluev.algorate.trader.HttpClientUtils.RichResponse
import io.circe.Printer
import io.circe.syntax._
import sttp.client3._
import sttp.model.Header
import sttp.model.MediaType

class HttpTelegramClient[F[_]](telegramUrl: String, sttpBackend: SttpBackend[F, Any])(implicit
    F: MonadError[F, Throwable]
) extends TelegramClient[F] {

  override def send(botToken: BotToken)(messageSource: TelegramClient.MessageSource): F[Unit] = {
    val json = Printer.noSpaces
      .copy(dropNullValues = true)
      .print(messageSource.asJson)
    def send(method: String) = basicRequest
      .post(uri"$telegramUrl/bot$botToken/$method")
      .header(Header.contentType(MediaType.ApplicationJson))
      .body(json)
      .send(sttpBackend)
      .checkStatusCode()
      .void
//    if (messageSource.photo.isDefined)
//      send("sendPhoto")
//    else
    send("sendMessage")
  }
}
