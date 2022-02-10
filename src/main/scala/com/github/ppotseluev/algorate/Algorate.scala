package com.github.ppotseluev.algorate

import cats.{Id, Parallel}
import cats.effect.kernel.{Async, Sync}
import cats.syntax.functor._
import com.github.ppotseluev.algorate.ai.{NeuroTradeFitness, NeuroTradingSignal}
import com.github.ppotseluev.algorate.core.{Point, TradingSignal}
import com.github.ppotseluev.algorate.model.{Price, Tags}
import com.github.ppotseluev.algorate.test.{TradingSignalTester, TradingSignalTesterImpl}
import com.github.ppotseluev.algorate.util.{Awaitable, Interval}
import com.github.ppotseluev.eann.evolutional.Evolution
import com.github.ppotseluev.eann.neural.Net
import com.softwaremill.tagging.Tagger

import java.time.OffsetDateTime
import java.util.concurrent.Executors

final class Algorate[F[_]: Async: Awaitable: Parallel](
    token: String,
    interval: Interval.Time = Interval.minutes(
      OffsetDateTime.parse("2021-12-06T10:15:30+03:00"),
      OffsetDateTime.parse("2021-12-06T22:15:30+03:00")
    )
) {
  val tester = new TradingSignalTesterImpl(
    token = token,
    ticker = "YNDX".taggedWith[Tags.Ticker],
    interval = interval
  )
  val syncTester: TradingSignalTester[Id] = (signal: TradingSignal) =>
    Awaitable[F].await(tester.test(signal))

  val priceList: List[Point] = Awaitable[F].await(tester.testData.source.compile.toList)
//  val avg = priceList.map(_.value.asInstanceOf[Double]).sum / priceList.size
  val normalizer: Price => Double = price => {
    price / priceList.head.value - 1
  }

  val signalConstructor: Net => TradingSignal = net =>
    new NeuroTradingSignal(
      net = net,
//    operationType = OperationType.Sell,
      normalizer = normalizer,
      takeProfitPercent = 0.5,
      stopLossPercent = 0.5
    )

  val fitnessFunc = new NeuroTradeFitness(syncTester, signalConstructor)

  private def evolve(): F[Net] = {
    val evolution = new Evolution(
      "evolution/parameters.yaml",
      fitnessFunc,
      Executors.newFixedThreadPool(8)
    )
    Sync[F].delay {
      evolution.evolve()
    }
  }

  val run: F[Unit] = {
    evolve().void
  }
}
