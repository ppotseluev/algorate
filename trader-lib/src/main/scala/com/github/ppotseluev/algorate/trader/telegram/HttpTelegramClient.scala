package com.github.ppotseluev.algorate.trader.telegram

import cats.MonadError
import cats.implicits._
import com.github.ppotseluev.algorate.trader.HttpClientUtils.RichResponse
import sttp.client3._

class HttpTelegramClient[F[_]](telegramUrl: String, sttpBackend: SttpBackend[F, Any])(implicit
    F: MonadError[F, Throwable]
) extends TelegramClient[F] {

  override def send(botToken: BotToken)(messageSource: TelegramClient.MessageSource): F[Unit] = {
//    val json = Printer.noSpaces
//      .copy(dropNullValues = true)
//      .print(messageSource.asJson)
//    val textRequest = basicRequest
//      .post(uri"$telegramUrl/bot$botToken/sendMessage")
//      .header(Header.contentType(MediaType.ApplicationJson))
//      .body(json) TODO clean up
    val photoRequest = basicRequest
      .post(uri"$telegramUrl/bot$botToken/sendPhoto")
      .multipartBody(
        multipart("photo", messageSource.photo).fileName("image"),
        multipart("chat_id", messageSource.chatId),
        multipart("caption", messageSource.text)
      )
    photoRequest.send(sttpBackend).checkStatusCode().void
//    def send(method: String) =
//      .send(sttpBackend)
//      .checkStatusCode()
//      .void
//    if (messageSource.photo.isDefined)
//      send("sendPhoto")
//    else
//    send("sendMessage")
  }
}
