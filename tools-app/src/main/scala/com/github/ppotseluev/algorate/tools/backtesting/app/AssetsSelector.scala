package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits._
import cats.kernel.Monoid
import com.github.ppotseluev.algorate.{TradingAsset, TradingStats}
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.tools.backtesting.Assets.Sampler
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.Period
import com.github.ppotseluev.algorate.tools.backtesting.SectorsResults
import com.github.ppotseluev.algorate.tools.backtesting.Testkit

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.mutable
import scala.concurrent.duration._

object AssetsSelector extends IOApp.Simple {
  private implicit val strategy = Strategies.createDefault(
    Params()
  )

  private implicit val sampler: Sampler = Sampler.All
  private val mode: Mode = Mode.Periods(
    Period.firstHalf(2021),
    Period.secondHalf(2021),
    Period.firstHalf(2022),
    Period.secondHalf(2022),
    Period.firstHalf(2023)
  )

  private val assets = selectedShares.sample
  private val selectionStrategy: SelectionStrategy = SelectAll
  private val candlesResolution = CandleResolution.FiveMinute

  private val testingToolkit = new Testkit[IO](skipNotFound = true)

  private val periods: List[Period] = mode match {
    case Mode.Periods(periods) => periods
    case Mode.Train =>
      List(
        Period(2021)
      )
    case Mode.Validate =>
      List(
        Period.firstHalf(2022)
      )
    case Mode.Test =>
      List(
        Period.secondHalf(2022)
      )
    case Mode.Test2 =>
      List(
        Period.firstHalf(2023)
      )
  }

  private val baseDir = {
    val saveTo = "tools-app/data/results"
    val startTime = System.currentTimeMillis().millis.toSeconds
    val id = s"${startTime}_${mode}_$sampler"
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

  periods.foreach { period =>
    val path = s"$baseDir/$period"
    Files.createDirectory(new File(path).toPath)
  }

  private def select(results: SectorsResults): Results = {
    val selected = selectionStrategy match {
      case ByProfit(selectionFactor) =>
        val allSharesResults = results.flatten.toList.sortBy { case (_, stats) =>
          stats.profit(fee = true).values.sum //FIXME
        }
        val n = (selectionFactor * allSharesResults.size).toInt
        allSharesResults.takeRight(n)
      case ByWinRatio(threshold) =>
        results.flatten.toList.filter { case (_, stats) =>
          stats.isEmpty || stats.totalWinRatio(fee = true) >= threshold
        }
      case ByProfitRatio(threshold) =>
        results.flatten.toList.filter { case (_, stats) =>
          stats.isEmpty ||
            stats.profitRatio(fee = true).values.sum >= threshold //FIXME
        }
      case SelectAll =>
        results.flatten.toList
      case ByStability(threshold) =>
        results.flatten.toList.filter { case (_, stats) =>
          val monthlyProfits =
            stats.monthly.values.map(_.profit(fee = true).values.sum).filter(_ != 0)
          val profitableMonthsCount = monthlyProfits.count(_ > 0)
          monthlyProfits.isEmpty ||
          profitableMonthsCount.toDouble / monthlyProfits.size >= threshold
        }
      case ByLowProfit(selectionFactor) =>
        val allSharesResults = results.flatten.toList.sortBy { case (_, stats) =>
          stats.profit(fee = true).values.sum //FIXME
        }
        val nonProfitable = allSharesResults.takeWhile(_._2.profit(fee = true).values.sum < 0)
        val n = (selectionFactor * nonProfitable.size).toInt
        allSharesResults.drop(n)
      case Filter(predicate) =>
        results.flatten.toList.filter { case (_, stats) => predicate(stats) }
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
          val stats = results.aggregatedStats
          val assetsCount = results.flatten.size
          printer.println("Monthly stats:")
          printer.println()
          val monthlyStats = new mutable.TreeMap[String, Int]()
          stats.monthly.foreach { case (month, stats) =>
            val monthProfit = stats.profit(fee = true).values.sum
            val p =
              if (monthProfit > 0) "+"
              else if (monthProfit == 0) "0"
              else "-"
            monthlyStats.updateWith(p)(x => (x.getOrElse(0) + 1).some)
            printer.println(s"($p) $month ${stats.show}")
          }
          printer.println()
          printer.println(s"Monthly stats: $monthlyStats")
          printer.println(s"total ($assetsCount assets): ${stats.show}")
          //todo print profitable/non-prfitable assets ratio
          val profitableCount = results.flatten.count(_._2.profit(fee = true).values.sum > 0)
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
      period: Period,
      testDuration: FiniteDuration
  ): IO[Unit] = {
    val path = s"$baseDir/$period"
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
  ): IO[SectorsResults] = periods match {
    case period :: restPeriods =>
      for {
        start <- IO(System.currentTimeMillis)
        _ <- IO {
          println(s"Start testing for $period")
        }
        candlesInterval = CandlesInterval(period.toInterval, candlesResolution)
        currentBatchResults <- testingToolkit.test(candlesInterval, assets)
        end <- IO(System.currentTimeMillis)
        results = select(currentBatchResults)
        _ <- save(results, period, (end - start).millis)
        res <- loopSelect(restPeriods, results.selectedAssetsList, accResult |+| results.original)
      } yield res
    case Nil => accResult.pure[IO]
  }

  override def run: IO[Unit] = {
    val start = System.currentTimeMillis()
    loopSelect(periods, assets, Monoid[SectorsResults].empty).map { res =>
      val end = System.currentTimeMillis()
      res -> (end - start).millis
    }
  }.flatMap { case (accResult, duration) =>
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

  case class ByLowProfit(exclusionFactor: Double) extends SelectionStrategy

  case object SelectAll extends SelectionStrategy

  case class ByProfit(selectionFactor: Double)
      extends SelectionStrategy //todo maybe be undefined if no loss....

  case class ByProfitRatio(threshold: Double) extends SelectionStrategy

  case class ByWinRatio(threshold: Double) extends SelectionStrategy //it makes more sense

  case class Filter(predicate: TradingStats => Boolean) extends SelectionStrategy

  case class ByStability(threshold: Double) extends SelectionStrategy

  sealed trait Mode

  object Mode {
    case object Train extends Mode

    case object Validate extends Mode

    case object Test extends Mode

    case object Test2 extends Mode

    case class Periods(periods: List[Period]) extends Mode
    object Periods {
      def apply(years: (Int, Int)): Periods = Periods(
        (years._1 to years._2).toList.map(Period(_))
      )
      def apply(periods: Period*): Periods = Periods(periods.toList)
    }
  }
}
