package com.github.ppotseluev.algorate.ta4j

import cats.Parallel
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.model.{Tags, Ticker}
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.util.{Awaitable, Interval, fromJavaFuture}
import com.softwaremill.tagging.Tagger
import org.ta4j.core.BarSeries
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.model.rest.SandboxRegisterRequest
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApi

import scala.jdk.CollectionConverters._

class BarSeriesProvider[F[_]: Async: Awaitable: Parallel](
    token: String
) {

  private val api: OpenApi = new OkHttpOpenApi(token, true)
  private val realBroker = new TinkoffBroker(api, "fake_acc_id".taggedWith[Tags.BrokerAccountId])

  Awaitable[F].await(init(api))

  private def init(api: OpenApi): F[Unit] =
    OptionT
      .whenF(api.isSandboxMode) {
        fromJavaFuture(api.getSandboxContext.performRegistration(new SandboxRegisterRequest))
      }
      .value
      .void

  private def get(ticker: Ticker, interval: Interval.Time): F[BarSeries] =
    for {
      instruments <- fromJavaFuture(
        api.getMarketContext.searchMarketInstrumentsByTicker(ticker)
      )
      Seq(instrument) = instruments.getInstruments.asScala.toSeq
      instrumentId = instrument.getFigi.taggedWith[Tags.InstrumentId]
      bars <- realBroker.getData(instrumentId, interval)
    } yield Utils.buildBarSeries(ticker, bars)

  def getBarSeries(ticker: Ticker, interval: Interval.Time): BarSeries =
    Awaitable[F].await { get(ticker, interval) }
}
