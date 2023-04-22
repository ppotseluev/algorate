package com.github.ppotseluev.algorate.tools.strategy.app.backtesting

import com.github.ppotseluev.algorate.TradingAsset

import scala.util.Random

object Assets {
  case class SampleSize(value: Int) extends AnyVal
  object SampleSize {
    implicit val default: SampleSize = SampleSize(5)
  }

  implicit class SampleSyntax(val assets: List[TradingAsset]) extends AnyVal {
    def sample(implicit size: SampleSize): List[TradingAsset] =
      Random.shuffle(assets).take(size.value)
  }

  val cryptocurrencies: List[TradingAsset] = List(
    "FUN",
    "HBAR",
    "MFT",
    "ARPA",
    "ICX",
    "FET",
    "FIL",
    "NKN",
    "STX",
    "WAN",
    "MITH",
    "QTUM",
    "COS",
    "BEAM",
    "BCH",
    "MTL",
    "OMG",
    "BTT",
    "RLC",
    "LTC",
    "KAVA",
    "XTZ",
    "AVAX",
    "MKR",
    "HOT",
    "LINK",
    "TCT",
    "DUSK",
    "PERL",
    "MATIC",
    "DENT",
    "BNT",
    "FTT",
    "THETA",
    "NANO",
    "IOTX",
    "ONT",
    "DREP",
    "ZEC",
    "GTO",
    "SOL",
    "XLM",
    "DOCK",
    "AAVE",
    "WIN",
    "VITE",
    "VET",
    "EOS",
    "ONG"
  ).map(TradingAsset.crypto)
}
