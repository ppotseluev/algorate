package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits._
import cats.kernel.Monoid
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester
import com.github.ppotseluev.algorate.tools.strategy.app.TestStrategy.SectorsResults
import com.github.ppotseluev.algorate.tools.strategy.app.backtesting.Assets._

import java.io.File
import fs2.Stream

import java.io.PrintWriter
import java.nio.file.{Files, Paths}
import java.time.{LocalDate, MonthDay}
import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share

import scala.concurrent.duration._

object AssetsSelector extends IOApp.Simple {

  private implicit val sampler: Sampler = Sampler
//    .All
    .SampleSize(400, seed = 4L.some)
  private val mode: Mode = Mode.Validate
  private val assets = shares.sample
  private val selectionStrategy: SelectionStrategy = SelectAll

  private val strategy = Strategies.default

  private val periods: List[Period] = mode match {
    case Mode.YearsRange(years) =>
      (years._1 to years._2).toList.map(Period(_))
    case Mode.Train =>
      List(
        Period(2020),
        Period(2021)
      )
    case Mode.Validate =>
      List(
        Period(2022, (MonthDay.of(1, 1) -> MonthDay.of(6, 30)).some)
      )
    case Mode.Test =>
      List(
        Period(2022, (MonthDay.of(7, 1) -> MonthDay.of(12, 31)).some)
      )
  }

  private val baseDir = {
    val saveTo = "tools-app/data/results"
    val startTime = System.currentTimeMillis().millis.toSeconds
    val id = s"${startTime}_$selectionStrategy"
    s"$saveTo/$id"
  }
  Files.createDirectory(new File(baseDir).toPath)
  private val strategyFilePath =
    "strategy/src/main/scala/com/github/ppotseluev/algorate/strategy/Strategies.scala"
  Files.copy(
    Paths.get(strategyFilePath),
    Paths.get(s"$baseDir/Strategies.scala")
  )

  periods.map(_.year).foreach { year =>
    val path = s"$baseDir/$year"
    Files.createDirectory(new File(path).toPath)
  }

  private def select(results: SectorsResults): Results = {
    val selected = selectionStrategy match {
      case ByProfit(selectionFactor) =>
        val allSharesResults = results.flatten.toList.sortBy { case (_, stats) =>
          stats.profit(fee = false).values.sum //FIXME
        }
        val n = (selectionFactor * allSharesResults.size).toInt
        allSharesResults.takeRight(n)
      case ByWinRatio(threshold) =>
        results.flatten.toList.filter { case (_, stats) =>
          stats.totalWinRatio(fee = false) >= threshold
        }
      case ByProfitRatio(threshold) =>
        results.flatten.toList.filter { case (_, stats) =>
          stats.profitRatio.values.sum >= threshold //FIXME
        }
      case SelectAll =>
        results.flatten.toList
    }
    val selectedResults = selected.foldMap { case (share, stats) => SectorsResults(share, stats) }
    Results(
      original = results,
      selected = selectedResults
    )
  }

  private def write(results: SectorsResults, path: String, testDuration: FiniteDuration): IO[Unit] =
    Resource.fromAutoCloseable(IO(new PrintWriter(path))).use { printer: PrintWriter =>
      IO.blocking {
        printer.println(results.show)
        val allStats = results.sectorsStats.values.flatMap(_.values).toList.combineAll
        printer.println()
        val assetsCount = results.flatten.size
        printer.println(s"total ($assetsCount assets): ${allStats.show}")
        printer.println()
        printer.println(s"Testing took ${testDuration.pretty}")
      }
    }

  private def writeAssets(results: SectorsResults, path: String): IO[Unit] =
    Resource.fromAutoCloseable(IO(new PrintWriter(path))).use { printer: PrintWriter =>
      IO.blocking {
        val text = results.assets.map(_.instrumentId).mkString("\n")
        printer.println(text)
      }
    }

  private def save(
      results: Results,
      year: Int,
      testDuration: FiniteDuration
  ): IO[Unit] = {
    val path = s"$baseDir/$year"
    for {
      _ <- write(results.original, s"$path/original.txt", testDuration) &>
        writeAssets(results.original, s"$path/original_assets.txt")
      _ <-
        if (results.selected.assets.size != results.original.assets.size) {
          write(results.selected, s"$path/selected.txt", testDuration) &>
            writeAssets(results.selected, s"$path/selected_assets.txt")
        } else {
          ().pure[IO]
        }
    } yield ()
  }

  private def test(done: AtomicInteger, total: Int) = (asset: TradingAsset, series: BarSeries) =>
    StrategyTester[IO](strategy).test(series, asset).map { stats =>
      val results = SectorsResults(asset, stats)
      println(s"done: ${(done.incrementAndGet().toDouble * 100 / total).toInt}%")
      results
    }

  private def testAll(period: Period, assets: List[TradingAsset])(implicit
      barSeriesProvider: BarSeriesProvider[IO]
  ): IO[SectorsResults] = {
    val interval = CandlesInterval(
      interval = period.toInterval,
      resolution = CandleResolution.OneMinute
    )
    val maxConcurrent = 8
    val counter = new AtomicInteger
    barSeriesProvider
      .streamBarSeries(assets, interval, maxConcurrent, skipNotFound = true)
      .parEvalMapUnordered(maxConcurrent)(test(counter, assets.size).tupled)
      .compile
      .toList
      .map(_.combineAll)
  }

  private def loopSelect(
      periods: List[Period],
      assets: List[TradingAsset],
      accResult: SectorsResults
  )(implicit
      barSeriesProvider: BarSeriesProvider[IO]
  ): IO[SectorsResults] = periods match {
    case period :: restPeriods =>
      for {
        start <- IO(System.currentTimeMillis)
        year = period.year
        _ <- IO {
          println(s"Start testing for $year year")
        }
        currentBatchResults <- testAll(period, assets)
        end <- IO(System.currentTimeMillis)
        results = select(currentBatchResults)
        _ <- save(results, year, (end - start).millis)
        res <- loopSelect(restPeriods, results.selectedAssetsList, accResult |+| results.original)
      } yield res
    case Nil => accResult.pure[IO]
  }

  override def run: IO[Unit] = Factory.io.tinkoffBroker
    .use { broker =>
      implicit val barSeriesProvider: BarSeriesProvider[IO] = new BarSeriesProvider(broker)
      val start = System.currentTimeMillis()
      loopSelect(periods, assets, Monoid[SectorsResults].empty).map { res =>
        val end = System.currentTimeMillis()
        res -> (end - start).millis
      }
    }
    .flatMap { case (accResult, duration) =>
      write(
        results = accResult,
        path = s"$baseDir/acc_results.txt",
        testDuration = duration
      )
    }

  private case class Results(
      original: SectorsResults,
      selected: SectorsResults
  ) {
    def selectedAssetsList: List[TradingAsset] = selected.assets.toList
  }

  sealed trait SelectionStrategy
  case object SelectAll extends SelectionStrategy
  case class ByProfit(selectionFactor: Double)
      extends SelectionStrategy //todo maybe be undefined if no loss....
  case class ByProfitRatio(threshold: Double) extends SelectionStrategy
  case class ByWinRatio(threshold: Double) extends SelectionStrategy //it makes more sense

  sealed trait Mode
  object Mode {
    case object Train extends Mode
    case object Validate extends Mode
    case object Test extends Mode
    case class YearsRange(years: (Int, Int)) extends Mode
  }

  case class Period(year: Int, range: Option[(MonthDay, MonthDay)] = None) {
    def start: LocalDate = {
      val md = range.fold(MonthDay.of(1, 1))(_._1)
      md.atYear(year)
    }

    def end: LocalDate = {
      val md = range.fold(MonthDay.of(12, 31))(_._2)
      md.atYear(year)
    }

    def toInterval: DaysInterval = DaysInterval(start, end)
  }
}
