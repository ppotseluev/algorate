package com.github.ppotseluev.algorate

case class TradingAsset(
    instrumentId: InstrumentId,
    ticker: Ticker,
    currency: Currency,
    sector: String = "UNKNOWN"
)

object TradingAsset {
  def crypto(ticker: Ticker, currency: Currency): TradingAsset =
    TradingAsset(ticker, ticker, currency, sector = "CRYPTO") //TODO

  def crypto(name: String): TradingAsset =
    crypto(ticker = s"${name}USDT", currency = "usdt")

  def share(instrumentId: InstrumentId): TradingAsset =
    TradingAsset(
      instrumentId = instrumentId,
      ticker = instrumentId,
      currency = "usdt", //TODO
    )
}
