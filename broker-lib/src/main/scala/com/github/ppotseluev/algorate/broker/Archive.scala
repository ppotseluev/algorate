package com.github.ppotseluev.algorate.broker

import cats.effect.Resource
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Price
import com.github.ppotseluev.algorate.broker.Archive.ArchiveCandle
import com.github.ppotseluev.algorate.broker.Archive.ArchiveNotFound
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.cats.CatsUtils._
import com.typesafe.scalalogging.LazyLogging
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import kantan.csv._
import kantan.csv.generic._
import kantan.csv.ops._
import scala.concurrent.duration.FiniteDuration
import scala.sys.process._

class Archive[F[_]: Sync](
    token: String,
    archiveDir: Path
) extends BarDataProvider[F]
    with LazyLogging {

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
        val dataId = s"${instrumentId}_$year"
        val basePath = archiveDir.resolve(dataId)
        val targetFile = basePath.resolve(s"$dayId.csv").toFile
        if (!basePath.toFile.exists()) {
          logger.info(s"Downloading $dataId")

          val scriptPath = Paths.get("tools-app/data/download.sh").toAbsolutePath.toString

          val envVars = Map("TINKOFF_TOKEN" -> token)
          val command = Seq(scriptPath, year.toString, instrumentId)

          // Get the script's parent directory as the working directory
          val workingDir = Paths.get(scriptPath).getParent.toFile

          val exitCode = Process(command, Some(workingDir), envVars.toSeq: _*).!

          exitCode match {
            case 0 => logger.debug(s"Successfully downloaded $dataId")
            case _ => throw new RuntimeException(s"Fail to download $dataId, error code $exitCode")
          }
        }
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
}

object Archive {
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
