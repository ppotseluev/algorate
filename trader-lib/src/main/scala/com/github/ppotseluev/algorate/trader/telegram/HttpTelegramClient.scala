package com.github.ppotseluev.algorate.trader.telegram

import cats.MonadError
import cats.implicits._
import com.github.ppotseluev.algorate.trader.HttpClientUtils.RichResponse
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.MessageSource
import io.circe.Printer
import io.circe.syntax._
import sttp.client3._
import sttp.model.{Header, MediaType}

class HttpTelegramClient[F[_]](telegramUrl: String, sttpBackend: SttpBackend[F, Any])(implicit
    F: MonadError[F, Throwable]
) extends TelegramClient[F] {

  override def send(botToken: BotToken)(messageSource: MessageSource): F[Unit] =
    if (messageSource.text.length > 1024) {
      val json = Printer.noSpaces
        .copy(dropNullValues = true)
        .print(messageSource.asJson)
      val sendText = basicRequest
        .post(uri"$telegramUrl/bot$botToken/sendMessage")
        .header(Header.contentType(MediaType.ApplicationJson))
        .body(json)
        .send(sttpBackend)
        .checkStatusCode()
        .void
      sendImage(botToken, messageSource.copy(text = "")) *> sendText
    } else {
      sendImage(botToken, messageSource)
    }

  private def sendImage(botToken: BotToken, source: MessageSource): F[Unit] = {
    val photoRequest = basicRequest
      .post(uri"$telegramUrl/bot$botToken/sendPhoto")
      .multipartBody(
        multipart("photo", source.photo).fileName("image"),
        multipart("chat_id", source.chatId),
        multipart("caption", source.text)
      )
    photoRequest.send(sttpBackend).checkStatusCode().void
  }
}
