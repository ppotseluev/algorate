package com.github.ppotseluev.algorate.broker

import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.ArchiveCachedBroker.ArchiveCandle
import com.github.ppotseluev.algorate.broker.Broker.{
  CandleResolution,
  CandlesInterval,
  OrderPlacementInfo
}
import com.typesafe.scalalogging.LazyLogging
import kantan.csv.ParseError.IOError
import kantan.csv._
import kantan.csv.ops._
import kantan.csv.generic._

import java.nio.file.Path
import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration

class ArchiveCachedBroker[F[_]: Sync](broker: Broker[F], archiveDir: Path)
    extends Broker[F]
    with LazyLogging {
  override def placeOrder(order: Order): F[OrderPlacementInfo] = broker.placeOrder(order)

  private val csvConfiguration = rfc.withCellSeparator(';')

  override def getData(
      instrumentId: InstrumentId,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = Sync[F].defer {
    val paths = candlesInterval.interval.days
      .map(_.localDate)
      .traverse { day =>
        val dayId = day.toString.replace("-", "")
        val basePath = archiveDir.resolve(s"${instrumentId}_${day.getYear}")
        val targetFile = basePath.resolve(s"$dayId.csv").toFile
        Either.cond(
          basePath.toFile.exists(),
          right = Option.when(targetFile.exists())(targetFile),
          left = IOError(s"$basePath doesn't exist"): ReadError
        )
      }
      .map(_.flatten)
    val candlesResolution = candlesInterval.resolution match {
      case CandleResolution.OneMinute => CandleResolution.OneMinute.duration
    } //matching to safely check that resolution is supported by the archive impl
    Sync[F].fromEither {
      paths
        .map(_.map(_.asCsvReader[ArchiveCandle](csvConfiguration)))
        .map(_.traverse(_.toList.sequence))
        .flatten
        .map(_.flatten.map(_.toBar(candlesResolution)))
    }
  }

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    broker.getOrderInfo(orderId)
}

object ArchiveCachedBroker {
  private case class ArchiveCandle(
      _id: String,
      startTime: String,
      open: Price,
      close: Price,
      high: Price,
      low: Price,
      volume: Int
  ) {
    def toBar(duration: FiniteDuration): Bar = Bar(
      openPrice = open,
      closePrice = close,
      lowPrice = low,
      highPrice = high,
      volume = volume,
      endTime = OffsetDateTime.parse(startTime).plusSeconds(duration.toSeconds),
      duration = duration
    )
  }
}
