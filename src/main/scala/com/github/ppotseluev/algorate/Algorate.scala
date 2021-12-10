package com.github.ppotseluev.algorate

import cats.data.OptionT
import cats.effect.kernel.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.core.{DummyTradingSignal, TradingBot}
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.util.{Interval, fromJavaFuture}
import com.softwaremill.tagging.Tagger
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.model.rest.SandboxRegisterRequest
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApi

import java.time.OffsetDateTime

abstract class Algorate[F[_]](implicit F: Async[F]) {
  private def init(api: OpenApi): F[Unit] =
    OptionT
      .whenF(api.isSandboxMode) {
        fromJavaFuture(api.getSandboxContext.performRegistration(new SandboxRegisterRequest))
      }
      .value
      .void

  private val tradingSignal = new DummyTradingSignal()

  def run(token: String): F[Unit] = {
    val api: OpenApi = new OkHttpOpenApi(token, true)
    val broker = new TinkoffBroker(api, "fake_acc_id".taggedWith[Tags.BrokerAccountId])
    for {
      _ <- init(api)
      instruments <- fromJavaFuture(
        api.getMarketContext.searchMarketInstrumentsByTicker("YNDX")
      )
      instrument = instruments.getInstruments.get(0) //todo require size=1
      instrumentId = instrument.getFigi.taggedWith[Tags.InstrumentId]
      now = OffsetDateTime.now()
      interval = Interval(now.minusHours(12), now.minusHours(2))
      source <- broker.getData(instrumentId, Some(interval))
      bot = new TradingBot(
        instrumentId = instrumentId,
        source = source,
        signal = tradingSignal,
        orderLimit = 100_000d.taggedWith[Tags.Price]
      )
      result <- bot.run.foreach(action => F.delay(println(action))).compile.drain
    } yield result
  }
}
