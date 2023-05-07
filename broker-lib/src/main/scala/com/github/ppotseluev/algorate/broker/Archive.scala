package com.github.ppotseluev.algorate.broker

import cats.Monad
import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Price
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingAsset.Type
import com.github.ppotseluev.algorate.broker.Archive.{
  ArchiveCandle,
  ArchiveNotFound,
  BinanceCandle,
  TinkoffCandle
}
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

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.duration.FiniteDuration
import scala.sys.process._

class Archive[F[_]: Sync](
    token: String,
    archiveDir: Path,
    downloadIfNotExist: Boolean = false //TODO
) extends BarDataProvider[F]
    with LazyLogging {

  private def readCsv[R[_]: Sync, T: HeaderDecoder](
      csvConfiguration: CsvConfiguration
  )(file: File): R[List[T]] =
    Resource
      .fromAutoCloseable(Sync[R].blocking(file.asCsvReader[T](csvConfiguration)))
      .use(reader => Sync[R].blocking(reader.toList.sequence).flatMap(_.toFT[R]))

  private def readAllCsv(assetType: TradingAsset.Type, candlesResolution: FiniteDuration)(
      files: List[File]
  ): F[List[Bar]] = {
    type F1[+T] = F[T @uncheckedVariance]
    val reader: File => F1[List[ArchiveCandle]] = assetType match {
      case Type.Crypto => readCsv[F1, BinanceCandle](rfc.withCellSeparator(','))
      case Type.Share  => readCsv[F1, TinkoffCandle](rfc.withCellSeparator(';'))
    }
    files.traverse(reader).map(_.flatten.map(_.toBar(candlesResolution)))
  }

  override def getData(
      asset: TradingAsset,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = Sync[F]
    .defer {
      val instrumentId = asset.instrumentId
      logger.debug(s"Getting data for $instrumentId")
      val paths = candlesInterval.interval.years
        .traverse { year =>
          val resolution = asset.`type` match {
            case Type.Crypto => candlesInterval.resolution
            case Type.Share  => CandleResolution.OneMinute
          }
          val dataPath = s"$resolution/${instrumentId}_$year"
          val baseDir = archiveDir.resolve(dataPath).toFile
          val done = if (downloadIfNotExist && !baseDir.exists()) {
            download(asset, year, candlesInterval.resolution)
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
            Either
              .cond(
                baseDir.exists(),
                right = files,
                left = ArchiveNotFound(instrumentId, year)
              )
              .toFT[F]
          }
        }
        .map(_.flatten.toList)
      paths.flatMap(readAllCsv(asset.`type`, candlesInterval.resolution.duration))
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
    .map { bars =>
      (asset.`type`, candlesInterval.resolution) match {
        case (TradingAsset.Type.Share, CandleResolution.FiveMinute) => Archive.downsample(bars, 5)
        case _                                                      => bars
      }
    }

  private def download(
      asset: TradingAsset,
      year: Int,
      resolution: CandleResolution
  ): F[Unit] = Sync[F].blocking {
    val instrumentId = asset.instrumentId
    val (provider, envVars) = asset.`type` match {
      case Type.Crypto => "binance" -> Nil
      case Type.Share  => "tinkoff" -> List("TINKOFF_TOKEN" -> token)
    }
    val dataId = s"${instrumentId}_$year"
    logger.info(s"Downloading $dataId")
    val scriptPath = Paths.get(s"tools-app/data/$provider/download.sh").toAbsolutePath.toString
    val command = Seq(scriptPath, year.toString, instrumentId, resolution.toString)

    // Get the script's parent directory as the working directory
    val workingDir = Paths.get(scriptPath).getParent.getParent.toFile

    val exitCode = Process(command, Some(workingDir), envVars: _*).!

    exitCode match {
      case 0 => logger.debug(s"Successfully downloaded $dataId")
      case _ => throw new RuntimeException(s"Fail to download $dataId, error code $exitCode")
    }
  }
}

object Archive {
  private def downsample(barSeries: List[Bar], factor: Int): List[Bar] = {
    barSeries
      .grouped(factor)
      .map { bars =>
        Bar(
          openPrice = bars.head.openPrice,
          closePrice = bars.last.closePrice,
          lowPrice = bars.map(_.lowPrice).min,
          highPrice = bars.map(_.highPrice).max,
          volume = bars.foldMap(_.volume),
          trades = bars.foldMap(_.trades),
          endTime = bars.last.endTime,
          duration = bars.foldMap(_.duration)
        )
      }
      .toList
  }

  case class ArchiveNotFound(instrumentId: InstrumentId, year: Int)
      extends RuntimeException(s"Archive ${instrumentId}_$year not found")

  trait ArchiveCandle {
    def toBar(duration: FiniteDuration): Bar
  }

  private case class TinkoffCandle(
      _id: String,
      startTime: String,
      open: Price,
      close: Price,
      high: Price,
      low: Price,
      volume: Int
  ) extends ArchiveCandle {
    def toBar(duration: FiniteDuration): Bar = Bar(
      openPrice = open,
      closePrice = close,
      lowPrice = low,
      highPrice = high,
      volume = volume,
      endTime = OffsetDateTime.parse(startTime).plusSeconds(duration.toSeconds),
      duration = duration,
      trades = 0
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
  ) extends ArchiveCandle {
    def toBar(duration: FiniteDuration): Bar = Bar(
      openPrice = open,
      closePrice = close,
      lowPrice = low,
      highPrice = high,
      volume = volume,
      endTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(closeTime), ZoneOffset.UTC),
      duration = duration,
      trades = numberOfTrades
    )
  }
}
