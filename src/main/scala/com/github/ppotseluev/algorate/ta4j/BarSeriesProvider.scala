package com.github.ppotseluev.algorate.ta4j

import cats.Parallel
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.model.{Tags, Ticker}
import com.github.ppotseluev.algorate.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.util.{Awaitable, Interval, fromJavaFuture}
import com.softwaremill.tagging.Tagger
import org.ta4j.core.BarSeries

import scala.jdk.CollectionConverters._

class BarSeriesProvider[F[_]: Async: Awaitable: Parallel](
    token: String
) {

  import ru.tinkoff.piapi.core.InvestApi

  val api = InvestApi.create(token)

  private val realBroker = new TinkoffBroker(api, "fake_acc_id".taggedWith[Tags.BrokerAccountId])

  private def get(ticker: Ticker, interval: Interval.Time): F[BarSeries] =
    for {
      instruments <- fromJavaFuture(
        api.getInstrumentsService.getAllShares
      )
      relatedInstruments = instruments.asScala.toList.filter(_.getTicker == ticker)
      instrument :: Nil = relatedInstruments
      instrumentId = instrument.getFigi.taggedWith[Tags.InstrumentId]
      bars <- realBroker.getData(instrumentId, interval)
    } yield Utils.buildBarSeries(ticker, bars)

  def getBarSeries(ticker: Ticker, interval: Interval.Time): BarSeries =
    Awaitable[F].await { get(ticker, interval) }
}
