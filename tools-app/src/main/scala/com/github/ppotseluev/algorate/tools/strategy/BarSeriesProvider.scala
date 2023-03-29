package com.github.ppotseluev.algorate.tools.strategy

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import com.github.ppotseluev.algorate.{BarsConverter, TradingAsset}
import com.github.ppotseluev.algorate.broker.Archive.ArchiveNotFound
import com.github.ppotseluev.algorate.broker.{BarDataProvider, Broker}
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import fs2.Stream

import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share

private[strategy] class BarSeriesProvider[F[_]: Async: Parallel](
    barDataProvider: BarDataProvider[F]
) {

  def getBarSeries(
      asset: TradingAsset,
      interval: CandlesInterval
  ): F[BarSeries] =
    for {
      bars <- barDataProvider.getData(asset.instrumentId, interval)
    } yield BarsConverter.buildBarSeries(asset.ticker, bars)

  private val fetched = new AtomicInteger

  def streamBarSeries(
      shares: List[Share],
      interval: CandlesInterval,
      maxConcurrent: Int,
      skipNotFound: Boolean = false
  ): Stream[F, (Share, BarSeries)] = {
    def asset(s: Share) =
      TradingAsset(
        instrumentId = s.getFigi,
        ticker = s.getTicker,
        currency = s.getCurrency
      )
    Stream
      .emits(shares)
      .parEvalMapUnordered[F, Option[(Share, BarSeries)]](maxConcurrent) { share =>
        getBarSeries(asset(share), interval)
          .map { d =>
//            println(s"fetched: ${fetched.incrementAndGet()}")
            (share -> d).some
          }
          .recover { case _: ArchiveNotFound if skipNotFound => None }
      }
      .flatMap(Stream.fromOption[F].apply(_))
  }
}
