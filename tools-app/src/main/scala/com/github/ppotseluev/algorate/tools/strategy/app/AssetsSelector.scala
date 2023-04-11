package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester
import com.github.ppotseluev.algorate.tools.strategy.TestSetup.strategy
import com.github.ppotseluev.algorate.tools.strategy.app.TestStrategy.SectorsResults
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share
import scala.concurrent.duration._

object AssetsSelector extends IOApp.Simple {
  private val factory = Factory.io

  private val years = 2019 -> 2022
  private val selectionStrategy: SelectionStrategy = ByWinRatio(threshold = 0.65)
  private val saveTo = "/Users/potseluev/IdeaProjects/algorate/tools-app/data/results"
  private val instrumentIds = factory.config.testInstrumentIds.orEmpty

  private def select(results: SectorsResults): Results = {
    val selected = selectionStrategy match {
      case ByProfit(selectionFactor) =>
        val allSharesResults = results.flatten.toList.sortBy { case (_, stats) =>
          stats.profit(fee = false).values.sum
        }
        val n = (selectionFactor * allSharesResults.size).toInt
        allSharesResults.takeRight(n)
      case ByWinRatio(threshold) =>
        results.flatten.toList.filter { case (_, stats) =>
          stats.totalWinRatio(fee = false) >= threshold
        }
    }
    val selectedResults = selected.foldMap { case (share, stats) => SectorsResults(share, stats) }
    Results(
      original = results,
      selected = selectedResults
    )
  }

  private val startTime = System.currentTimeMillis().millis.toSeconds
  Files.createDirectory(new File(s"$saveTo/$startTime").toPath)

  private def write(results: SectorsResults, path: String, testDuration: FiniteDuration): IO[Unit] =
    Resource.fromAutoCloseable(IO(new PrintWriter(path))).use { printer: PrintWriter =>
      IO.blocking {
        printer.println(results.show)
        val allStats = results.sectorsStats.values.flatMap(_.values).toList.combineAll
        printer.println()
        val assetsCount = results.flatten.size
        printer.println(s"total ($assetsCount assets): ${allStats.show}")
        printer.println()
        printer.println(s"Testing took $testDuration")
      }
    }

  private def save(
      results: Results,
      year: Int,
      testDuration: FiniteDuration
  ): IO[Unit] = {
    val baseDir = s"$saveTo/$startTime"
    for {
      _ <- write(results.original, s"$baseDir/${year}_original", testDuration)
      _ <- write(results.selected, s"$baseDir/${year}_selected", testDuration)
    } yield ()
  }

  private def test(done: AtomicInteger) = (share: Share, series: BarSeries) =>
    IO.blocking {
      val asset = TradingAsset(
        instrumentId = share.getFigi,
        ticker = share.getTicker,
        currency = share.getCurrency
      )
      //      println(s"started: ${started.incrementAndGet()}")
      val stats = StrategyTester(strategy).test(series, asset)
      val results = SectorsResults(share, stats)
      println(s"done: ${done.incrementAndGet()}")
      results
    }

  private def testAll(year: Int, shares: List[Share])(implicit
      barSeriesProvider: BarSeriesProvider[IO]
  ): IO[SectorsResults] = {
    val interval = CandlesInterval(
      interval = DaysInterval(
        start = LocalDate.of(year, 1, 1),
        end = LocalDate.of(year, 12, 31)
      ),
      resolution = CandleResolution.OneMinute
    )
    val maxConcurrent = 8
    val counter = new AtomicInteger
    barSeriesProvider
      .streamBarSeries(shares, interval, maxConcurrent, skipNotFound = true)
      .parEvalMapUnordered(maxConcurrent)(test(counter).tupled)
      .compile
      .toList
      .map(_.combineAll)
  }

  private def loopSelect(year: Int, shares: List[Share])(implicit
      barSeriesProvider: BarSeriesProvider[IO]
  ): IO[Unit] =
    for {
      start <- IO(System.currentTimeMillis)
      _ <- IO {
        println(s"Start testing for $year year")
      }
      currentBatchResults <- testAll(year, shares)
      end <- IO(System.currentTimeMillis)
      results = select(currentBatchResults)
      _ <- save(results, year, (end - start).millis)
      newYear = year + 1
      _ <-
        if (newYear <= years._2) {
          loopSelect(newYear, results.selectedShares)
        } else {
          ().pure[IO]
        }
    } yield ()

  override def run: IO[Unit] = factory.tinkoffBroker.use { broker =>
    broker.getSharesById(instrumentIds).flatMap { shares =>
      implicit val barSeriesProvider: BarSeriesProvider[IO] = new BarSeriesProvider(broker)
      loopSelect(years._1, shares)
    }
  }

  private case class Results(
      original: SectorsResults,
      selected: SectorsResults
  ) {
    def selectedShares: List[Share] = selected.flatten.keySet.toList
  }

  sealed trait SelectionStrategy
  case class ByProfit(fraction: Double) extends SelectionStrategy
  case class ByWinRatio(threshold: Double) extends SelectionStrategy
}
