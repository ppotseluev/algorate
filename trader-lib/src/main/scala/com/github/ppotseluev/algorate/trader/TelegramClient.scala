package com.github.ppotseluev.algorate.trader

import com.github.ppotseluev.algorate.trader.TelegramClient._
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.semiauto.deriveCodec

trait TelegramClient[F[_]] {
  def send(botToken: BotToken)(messageSource: MessageSource): F[Unit]
}

object TelegramClient {
  type BotToken = String
  implicit private val circeConfig: Configuration = Configuration.default.withSnakeCaseMemberNames

  @ConfiguredJsonCodec
  case class KeyboardButton(text: String)

  object KeyboardButton {
    implicit val keyboardButtonCodec: Codec[KeyboardButton] = deriveCodec
  }

  @ConfiguredJsonCodec
  case class ReplyMarkup(
      keyboard: Option[Seq[Seq[KeyboardButton]]] = None,
      removeKeyboard: Option[Boolean] = None
  )

  object ReplyMarkup {
    implicit val keyboardCodec: Codec[ReplyMarkup] = deriveCodec
  }

  @ConfiguredJsonCodec
  case class MessageSource(
      chatId: String,
      text: String,
      photo: Option[Array[Byte]],
      replyMarkup: Option[ReplyMarkup],
      parseMode: Option[String] = Some("MarkdownV2")
  )

  object MessageSource {
    implicit val messageSourceCodec: Codec[MessageSource] = deriveCodec
  }

}
