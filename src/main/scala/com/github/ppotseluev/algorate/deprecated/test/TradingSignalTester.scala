package com.github.ppotseluev.algorate.deprecated.test

import com.github.ppotseluev.algorate.core.TestBroker.TradingStatistics
import com.github.ppotseluev.algorate.deprecated.TradingSignal

trait TradingSignalTester[F[_]] {
  def test(signal: TradingSignal): F[TradingStatistics]
}
