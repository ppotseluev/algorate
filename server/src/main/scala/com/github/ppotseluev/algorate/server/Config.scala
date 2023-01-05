package com.github.ppotseluev.algorate.server

import scala.concurrent.duration.FiniteDuration

case class Config(
    tinkoffAccessToken: String,
    accountId: String,
    telegramBotToken: String,
    telegramChatId: String,
    telegramWebhookSecret: String,
    candlesMinInterval: FiniteDuration,
    telegramUrl: String,
    telegramUsersWhitelist: Set[Int]
) {
  def telegramTrackedChats: Set[String] = Set(telegramChatId)
}
