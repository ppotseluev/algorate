package com.github.ppotseluev.algorate.trader.telegram

import com.github.ppotseluev.algorate.trader.telegram.TelegramClient._
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.semiauto.deriveCodec

trait TelegramClient[F[_]] {
  def send(botToken: BotToken)(messageSource: Message): F[Unit]
}

object TelegramClient {
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
    def make(
        buttons: Seq[KeyboardButton],
        removeKeyboard: Option[Boolean] = None,
        groupBy: Int = 5,
        sortButtons: Boolean = true
    ): ReplyMarkup = {
      val keyboard = if (sortButtons) buttons.sortBy(_.text) else buttons
      ReplyMarkup(
        keyboard = Some(keyboard.grouped(groupBy).toSeq),
        removeKeyboard = None
      )
    }

    implicit val keyboardCodec: Codec[ReplyMarkup] = deriveCodec
  }

  @ConfiguredJsonCodec
  case class Message(
      chatId: String,
      text: String,
      @transient photo: Option[Array[Byte]] = None,
      replyMarkup: Option[ReplyMarkup] = None,
      parseMode: Option[String] = None, //Some("MarkdownV2")
      disableWebPagePreview: Option[Boolean] = None
  )

  object Message {
    implicit val messageCodec: Codec[Message] = deriveCodec
  }

  case class MessageSource(
      text: String,
      photo: Option[Array[Byte]] = None,
      replyMarkup: Option[ReplyMarkup] = None,
      parseMode: Option[String] = None //Some("MarkdownV2")
  ) {
    def toMessage(chatId: String) = Message(
      chatId = chatId,
      text = text,
      photo = photo,
      replyMarkup = replyMarkup,
      parseMode = parseMode
    )
  }
}
