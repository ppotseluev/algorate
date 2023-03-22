package com.github.ppotseluev.algorate.tools.strategy

import cats.Parallel
import cats.effect.Async
import cats.implicits._
import com.github.ppotseluev.algorate.BarsConverter
import com.github.ppotseluev.algorate.broker.ArchiveCachedBroker.ArchiveNotFound
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import fs2.Stream

import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share

private[strategy] class BarSeriesProvider[F[_]: Async: Parallel](broker: Broker[F]) {

  def getBarSeries(
      share: Share,
      interval: CandlesInterval
  ): F[BarSeries] =
    for {
      bars <- broker.getData(share.getFigi, interval)
    } yield BarsConverter.buildBarSeries(share.getTicker, bars)

  private val fetched = new AtomicInteger

  def streamBarSeries(
      shares: List[Share],
      interval: CandlesInterval,
      maxConcurrent: Int,
      skipNotFound: Boolean = false
  ): Stream[F, (Share, BarSeries)] =
    Stream
      .emits(shares)
      .parEvalMapUnordered[F, Option[(Share, BarSeries)]](maxConcurrent) { share =>
        getBarSeries(share, interval)
          .map { d =>
//            println(s"fetched: ${fetched.incrementAndGet()}")
            (share -> d).some
          }
          .recover { case _: ArchiveNotFound if skipNotFound => None }
      }
      .flatMap(Stream.fromOption[F].apply(_))
}
