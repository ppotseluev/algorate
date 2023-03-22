package com.github.ppotseluev.algorate.tools.strategy.app

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester
import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share
import scala.concurrent.duration._

/**
 * Tests strategy based on [[TestSetup]] data
 */
object TestStrategy extends IOApp {
  import com.github.ppotseluev.algorate.tools.strategy.TestSetup._

  val countDone = new AtomicInteger()
  val countStarted = new AtomicInteger()

  private val test = (share: Share, series: BarSeries) =>
    IO.blocking {
      val started = countStarted.incrementAndGet()
      println(s"started: $started")
      val stats = StrategyTester(strategy).test(series)
      val results = SectorsResults(share, stats)
      val done = countDone.incrementAndGet()
      println(s"done: $done")
      results
    }

  override def run(args: List[String]): IO[ExitCode] = {
    Factory.io.tinkoffBroker.use { broker =>
      val start = System.currentTimeMillis()
      val barSeriesProvider = new BarSeriesProvider(broker)

      def testAll(shares: List[Share]): IO[SectorsResults] = {
        val maxConcurrent = 8
        println("start testing")
        barSeriesProvider
          .streamBarSeries(shares, interval, maxConcurrent, skipNotFound = true)
          .parEvalMapUnordered(maxConcurrent)(test.tupled)
          .compile
          .toList
          .map(_.combineAll)
      }

      broker
        .getSharesById(ids.toSet)
        .flatMap(testAll)
        .map { res =>
          println(res.show)
          val allStats = res.sectorsStats.values.flatMap(_.values).toList.combineAll
          println(s"total: $allStats")
          println()
          println("per month statistics")
          allStats.monthly.foreach { case (month, stats) =>
            println(s"$month $stats")
          }
          println()
          val end = System.currentTimeMillis()
          val duration = (end - start).millis
          println(s"Testing took $duration")
        }
        .as(ExitCode.Success)
    }
  }

  implicit val tickersShow: Show[Map[Share, TradingStats]] = (stats: Map[Share, TradingStats]) =>
    stats
      .map { case (share, stats) =>
        s"${share.getTicker} (${share.getName}): $stats"
      }
      .mkString("\n")

  case class SectorsResults(
      sectorsStats: Map[String, Map[Share, TradingStats]]
  )

  object SectorsResults {
    implicit val show: Show[SectorsResults] = res =>
      res.sectorsStats.map { case (sector, value) =>
        s"""
          |Sector: $sector
          |${value.show} 
          |""".stripMargin
      }.mkString

    implicit val monoid: Monoid[SectorsResults] = semiauto.monoid

    def apply(share: Share, stats: TradingStats): SectorsResults = SectorsResults(
      Map(
        share.getSector -> Map(share -> stats)
      )
    )
  }
}
