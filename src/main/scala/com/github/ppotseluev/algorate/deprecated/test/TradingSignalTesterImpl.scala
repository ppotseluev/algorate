//package com.github.ppotseluev.algorate.deprecated.test
//
//import cats.Parallel
//import cats.data.OptionT
//import cats.effect.Concurrent
//import cats.effect.kernel.Async
//import cats.syntax.flatMap._
//import cats.syntax.functor._
//import com.github.ppotseluev.algorate.core.{Point, TradingBot, TradingSignal}
//import com.github.ppotseluev.algorate.model.{InstrumentId, Tags, Ticker}
//import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
//import com.github.ppotseluev.algorate.util.{Awaitable, Interval, fromJavaFuture}
//import com.softwaremill.tagging.Tagger
//import fs2.Stream
//
//final class TradingSignalTesterImpl[F[_]: Concurrent: Async: Awaitable: Parallel](
//    token: String,
//    ticker: Ticker,
//    interval: Interval.Time
//) extends TradingSignalTester[F]
//    with AutoCloseable {
//
//  case class TestData(
//      instrumentId: InstrumentId,
//      source: Stream[F, Point]
//  )
//
//  private val api: OpenApi = new OkHttpOpenApi(token, true)
//  private val realBroker = new TinkoffBroker(api, "fake_acc_id".taggedWith[Tags.BrokerAccountId])
//
//  val testData: TestData = {
//    val f: F[TestData] = for {
//      _ <- init(api)
//      instruments <- fromJavaFuture(
//        api.getMarketContext.searchMarketInstrumentsByTicker(ticker)
//      )
//      Seq(instrument) = instruments.getInstruments.asScala.toSeq
//      instrumentId = instrument.getFigi.taggedWith[Tags.InstrumentId]
//      bars <- realBroker.getData(instrumentId, interval)
//      source = Stream.emits(bars.map(bar => Point(bar.endTime, bar.closePrice)))
//      _ = println(s"Use instrument ${instrument.getFigi}")
//    } yield TestData(instrumentId, source)
//    Awaitable[F].await(f)
//  }
//
//  private def init(api: OpenApi): F[Unit] =
//    OptionT
//      .whenF(api.isSandboxMode) {
//        fromJavaFuture(api.getSandboxContext.performRegistration(new SandboxRegisterRequest))
//      }
//      .value
//      .void
//
//  override def test(signal: TradingSignal): F[TradingStatistics] = {
//    val broker = new TestBroker(realBroker)
//    val bot = new TradingBot(
//      instrumentId = testData.instrumentId,
//      source = testData.source,
//      signal = signal,
//      orderLimit = 100_000d.taggedWith[Tags.Price],
//      broker = broker
//    )
//    for {
//      _ <- bot.run.compile.drain
//      statistics = broker.getStatistics(testData.instrumentId, includeOpenedPosition = false)
//    } yield statistics
//  }
//
//  override def close(): Unit =
//    api.close()
//}
