package com.github.ppotseluev.algorate

case class TradingAsset(
    instrumentId: InstrumentId,
    ticker: Ticker,
    currency: Currency,
    `type`: TradingAsset.Type,
    sector: String = "UNKNOWN"
)

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
      currency = "usdt" //TODO
    )
}
