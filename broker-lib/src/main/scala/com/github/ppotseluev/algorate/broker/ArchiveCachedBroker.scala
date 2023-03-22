package com.github.ppotseluev.algorate.broker

import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.ArchiveCachedBroker.ArchiveCandle
import com.github.ppotseluev.algorate.broker.ArchiveCachedBroker.ArchiveNotFound
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.cats.CatsUtils._
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import java.nio.file.Path
import java.time.OffsetDateTime
import kantan.csv._
import kantan.csv.generic._
import kantan.csv.ops._
import scala.concurrent.duration.FiniteDuration

class ArchiveCachedBroker[F[_]: Sync](broker: Broker[F], archiveDir: Path)
    extends Broker[F]
    with LazyLogging {
  override def placeOrder(order: Order): F[OrderPlacementInfo] = broker.placeOrder(order)

  private val csvConfiguration = rfc.withCellSeparator(';')

  private def readCsv(file: File): F[List[ArchiveCandle]] =
    Resource
      .fromAutoCloseable(Sync[F].blocking(file.asCsvReader[ArchiveCandle](csvConfiguration)))
      .use(reader => Sync[F].blocking(reader.toList.sequence).flatMap(_.toFT[F]))

  private def readAllCsv(files: List[File]): F[List[List[ArchiveCandle]]] =
    files.traverse(readCsv)

  override def getData(
      instrumentId: InstrumentId,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = Sync[F].defer {
    val paths = candlesInterval.interval.days
      .map(_.localDate)
      .traverse { day =>
        val dayId = day.toString.replace("-", "")
        val year = day.getYear
        val basePath = archiveDir.resolve(s"${instrumentId}_$year")
        val targetFile = basePath.resolve(s"$dayId.csv").toFile
        Either.cond(
          basePath.toFile.exists(),
          right = Option.when(targetFile.exists())(
            targetFile
          ), //TODO handle cases when concrete day is missed or the whole archive is missed
          left = ArchiveNotFound(instrumentId, year)
        )
      }
      .map(_.flatten)
    val candlesResolution = candlesInterval.resolution match {
      case CandleResolution.OneMinute => CandleResolution.OneMinute.duration
    } //matching to safely check that resolution is supported by the archive impl
    paths
      .toFT[F]
      .flatMap(readAllCsv)
      .map(_.flatten.map(_.toBar(candlesResolution)))
  }

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    broker.getOrderInfo(orderId)
}

object ArchiveCachedBroker {
  case class ArchiveNotFound(instrumentId: InstrumentId, year: Int)
      extends RuntimeException(s"Archive ${instrumentId}_$year not found")

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
