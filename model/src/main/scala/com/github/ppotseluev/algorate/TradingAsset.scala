package com.github.ppotseluev.algorate

case class TradingAsset(
    instrumentId: InstrumentId,
    ticker: Ticker,
    currency: Currency
)

object TradingAsset {
  def crypto(ticker: Ticker, currency: Currency): TradingAsset =
    TradingAsset(ticker, ticker, currency)
}
