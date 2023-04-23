package com.github.ppotseluev.algorate.broker

import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Price
import com.github.ppotseluev.algorate.broker.Archive.ArchiveNotFound
import com.github.ppotseluev.algorate.broker.Archive.BinanceCandle
import com.github.ppotseluev.algorate.broker.Archive.TinkoffCandle
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.cats.CatsUtils._
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kantan.csv._
import kantan.csv.generic._
import kantan.csv.ops._
import scala.concurrent.duration.FiniteDuration
import scala.sys.process._

class Archive[F[_]: Sync](
    token: String,
    archiveDir: Path,
    downloadIfNotExist: Boolean = true
) extends BarDataProvider[F]
    with LazyLogging {

  private def readCsv[T: HeaderDecoder](
      csvConfiguration: CsvConfiguration
  )(file: File): F[List[T]] =
    Resource
      .fromAutoCloseable(Sync[F].blocking(file.asCsvReader[T](csvConfiguration)))
      .use(reader => Sync[F].blocking(reader.toList.sequence).flatMap(_.toFT[F]))

  private def readAllCsv(candlesResolution: FiniteDuration)(files: List[File]): F[List[Bar]] =
    files
      .traverse(readCsv[TinkoffCandle](rfc.withCellSeparator(';')))
      .map(_.flatten.map(_.toBar(candlesResolution)))
      .recoverWith { case _: kantan.codecs.error.Error =>
        files
          .traverse(readCsv[BinanceCandle](rfc.withCellSeparator(',')))
          .map(_.flatten.map(_.toBar(candlesResolution)))
      }

  override def getData(
      instrumentId: InstrumentId,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = Sync[F]
    .defer {
      logger.debug(s"Getting data for $instrumentId")
      val paths = candlesInterval.interval.years
        .traverse { year =>
          val dataId = s"${instrumentId}_$year"
          val baseDir = archiveDir.resolve(dataId).toFile
          val done = if (downloadIfNotExist && !baseDir.exists()) {
            download(instrumentId, year)
          } else {
            ().pure[F]
          }
          def files = baseDir
            .listFiles { file =>
              val name = file.getName
              name.endsWith(".csv") && {
                val month = name.slice(4, 6).toInt
                candlesInterval.interval.contains(year, month)
              }
            }
            .sortBy(_.getName)
          done.flatMap { _ =>
            Either.cond(
              baseDir.exists(),
              right = files,
              left = ArchiveNotFound(instrumentId, year)
            ).toFT[F]
          }
        }
        .map(_.flatten.toList)
      val candlesResolution = candlesInterval.resolution match {
        case CandleResolution.OneMinute => CandleResolution.OneMinute.duration
      } //matching to safely check that resolution is supported by the archive impl
      paths.flatMap(readAllCsv(candlesResolution))
    }
    .map { bars =>
      bars
        .dropWhile { bar =>
          !candlesInterval.interval.contains(bar.endTime)
        }
        .takeWhile { bar =>
          candlesInterval.interval.contains(bar.endTime)
        }
    }

  private def download(instrumentId: InstrumentId, year: Int): F[Unit] = Sync[F].blocking {
    val dataId = s"${instrumentId}_$year"
    logger.info(s"Downloading $dataId")
    val scriptPath = Paths.get("tools-app/data/binance/download.sh").toAbsolutePath.toString
//    val envVars = Map("TINKOFF_TOKEN" -> token)
    val command = Seq(scriptPath, year.toString, instrumentId)

    // Get the script's parent directory as the working directory
    val workingDir = Paths.get(scriptPath).getParent.getParent.toFile

    val exitCode = Process(command, Some(workingDir)).!

    exitCode match {
      case 0 => logger.debug(s"Successfully downloaded $dataId")
      case _ => throw new RuntimeException(s"Fail to download $dataId, error code $exitCode")
    }
  }
}

object Archive {
  case class ArchiveNotFound(instrumentId: InstrumentId, year: Int)
      extends RuntimeException(s"Archive ${instrumentId}_$year not found")

  private case class TinkoffCandle(
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

  private case class BinanceCandle(
      openTime: Long,
      open: Price,
      high: Price,
      low: Price,
      close: Price,
      volume: Double,
      closeTime: Long,
      quoteAssetVolume: Double,
      numberOfTrades: Int,
      takerBuyBaseAssetVolume: Double,
      takerBuyQuoteAssetVolume: Double
  ) {
    def toBar(duration: FiniteDuration): Bar = Bar(
      openPrice = open,
      closePrice = close,
      lowPrice = low,
      highPrice = high,
      volume = volume,
      endTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(closeTime), ZoneOffset.UTC),
      duration = duration
    )
  }
}
