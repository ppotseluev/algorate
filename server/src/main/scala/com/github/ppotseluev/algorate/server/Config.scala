package com.github.ppotseluev.algorate.server

import com.github.ppotseluev.algorate.trader.Api
import scala.concurrent.duration.FiniteDuration

case class Config(
    tinkoffAccessToken: String,
    accountId: String,
    telegramBotToken: String,
    telegramChatId: String,
    telegramWebhookSecret: String,
    candlesMinInterval: FiniteDuration,
    telegramUrl: String,
    telegramUsersWhitelist: Set[Int],
    apiConfig: Api.Config
) {
  def telegramTrackedChats: Set[String] = Set(telegramChatId)
}
