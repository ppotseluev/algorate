package com.github.ppotseluev.algorate.deprecated.test

import com.github.ppotseluev.algorate.deprecated.TradingSignal

import TestBroker.TradingStatistics

trait TradingSignalTester[F[_]] {
  def test(signal: TradingSignal): F[TradingStatistics]
}
