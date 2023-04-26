package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits._
import cats.kernel.Monoid
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.tools.backtesting.Assets.Sampler
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.Assets.shares
import com.github.ppotseluev.algorate.tools.backtesting.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.backtesting.Period
import com.github.ppotseluev.algorate.tools.backtesting.SectorsResults
import com.github.ppotseluev.algorate.tools.backtesting.Testkit
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.time.MonthDay
import scala.concurrent.duration._

object AssetsSelector extends IOApp.Simple {
//TODO consider not splitting dataset for more accurate results
  private implicit val sampler: Sampler = Sampler.All
//    .SampleSize(10)
  private val mode: Mode = Mode.Validate
  private val assets = (cryptocurrencies.sample) // ++ cryptocurrencies.sample).sample
  private val selectionStrategy: SelectionStrategy = SelectAll

  private implicit val strategy = Strategies.default

  private val testingToolkit = new Testkit[IO](skipNotFound = true)

  private val periods: List[Period] = mode match {
    case Mode.YearsRange(years) =>
      (years._1 to years._2).toList.map(Period(_))
    case Mode.Train =>
      List(
//        Period(2020),
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
    case Mode.Test2 =>
      List(
        Period(2023, (MonthDay.of(1, 1) -> MonthDay.of(4, 25)).some)
      )
  }

  private val baseDir = {
    val saveTo = "tools-app/data/results"
    val startTime = System.currentTimeMillis().millis.toSeconds
    val id = s"${startTime}_$mode"
    s"$saveTo/$id"
  }
  Files.createDirectory(new File(baseDir).toPath)
  private val strategyFilePath =
    "strategy/src/main/scala/com/github/ppotseluev/algorate/strategy/Strategies.scala"
  Files.copy(
    Paths.get(strategyFilePath),
    Paths.get(s"$baseDir/Strategies.scala")
  )
  private val assetsSelectorPath =
    "/Users/potseluev/IdeaProjects/algorate/tools-app/src/main/scala/com/github/ppotseluev/algorate/tools/backtesting/app/AssetsSelector.scala"
  Files.copy(
    Paths.get(assetsSelectorPath),
    Paths.get(s"$baseDir/AssetsSelector.scala")
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
          stats.profitRatio(fee = false).values.sum >= threshold //FIXME
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
        val outliers = results.flatten.collect {
          case (asset, stats)
              if stats.profitRatio(false).values.sum > 10 && stats.totalPositions > 10 =>
            asset
        }.toSet
        def printStats(results: SectorsResults) = {
          printer.println()
          val assetsCount = results.flatten.size
          printer.println(s"total ($assetsCount assets): ${results.aggregatedStats.show}")
          //todo print profitable/non-prfitable assets ratio
          val profitableCount = results.flatten.count(_._2.profitRatio(false).values.sum > 1)
          val hasTradesCount = Option(
            results.flatten.count(_._2.totalPositions > 0)
          ).filter(_ > 0).getOrElse(1)
          printer.println(s"profitable: ${profitableCount * 100 / hasTradesCount}%")
        }
        printStats(results)
        if (outliers.nonEmpty) {
          printer.println()
          printer.println(s"Without outliers ${outliers.map(_.instrumentId)}:")
          printStats(results.exclude(outliers))
        }
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
        currentBatchResults <- testingToolkit.test(period.toInterval, assets)
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

    case object Test2 extends Mode

    case class YearsRange(years: (Int, Int)) extends Mode
  }
}
