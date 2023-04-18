package com.github.ppotseluev.algorate.server

import com.github.ppotseluev.algorate.TradingAsset
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
    apiConfig: Api.Config,
    enableBrokerCache: Boolean,
    historicalDataArchive: Option[String]
) {
  def telegramTrackedChats: Set[String] = Set(telegramChatId)

  def assets: List[TradingAsset] = List(
    "BTC",
    "ETH",
    "ADA",
    "BNB",
    "SOL",
    "XRP",
    "DOT",
    "DOGE",
    "LINK",
    "AVAX",
    "ALGO",
    "LTC",
    "WBTC",
    "ATOM",
    "MATIC",
    "XLM",
    "ICP",
    "ETC",
    "VET",
    "FIL",
    "UNI",
    "AAVE",
    "THETA",
    "XMR",
    "TRX",
    "MKR",
    "EOS",
    "KSM",
    "CRO",
    "CAKE"
  ).map(TradingAsset.crypto)
}
