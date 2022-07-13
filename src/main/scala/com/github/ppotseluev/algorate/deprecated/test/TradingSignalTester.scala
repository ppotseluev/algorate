package com.github.ppotseluev.algorate.deprecated.test

import TestBroker.TradingStatistics
import com.github.ppotseluev.algorate.deprecated.TradingSignal

trait TradingSignalTester[F[_]] {
  def test(signal: TradingSignal): F[TradingStatistics]
}
