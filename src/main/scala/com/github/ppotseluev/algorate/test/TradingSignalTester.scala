package com.github.ppotseluev.algorate.test

import com.github.ppotseluev.algorate.core.TradingSignal
import com.github.ppotseluev.algorate.test.TestBroker.TradingStatistics

trait TradingSignalTester[F[_]] {
  def test(signal: TradingSignal): F[TradingStatistics]
}
