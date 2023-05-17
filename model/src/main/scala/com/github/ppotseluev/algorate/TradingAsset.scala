package com.github.ppotseluev.algorate

import com.github.ppotseluev.algorate.TradingAsset.Type

case class TradingAsset(
    instrumentId: InstrumentId,
    ticker: Ticker,
    currency: Currency,
    `type`: TradingAsset.Type,
    sector: String
) {
  def isCrypto: Boolean = `type` match {
    case Type.Crypto => true
    case Type.Share  => false
  }

  def isShare: Boolean = `type` match {
    case Type.Crypto => false
    case Type.Share  => true
  }
}

object TradingAsset {
  sealed trait Type
  object Type {
    case object Crypto extends Type
    case object Share extends Type
  }

  def crypto(ticker: Ticker, currency: Currency): TradingAsset =
    TradingAsset(ticker, ticker, currency, `type` = Type.Crypto, sector = "CRYPTO") //TODO

  def crypto(name: String): TradingAsset =
    crypto(ticker = s"${name}USDT", currency = "usdt")

  def share(instrumentId: InstrumentId): TradingAsset =
    TradingAsset(
      instrumentId = instrumentId,
      ticker = instrumentId,
      `type` = Type.Share,
      currency = "usd",
      sector = "SHARES"
    )
}
