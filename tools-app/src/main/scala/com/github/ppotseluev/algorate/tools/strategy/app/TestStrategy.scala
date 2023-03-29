package com.github.ppotseluev.algorate.tools.strategy.app

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.{TradingAsset, TradingStats}
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester

import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share

import scala.collection.immutable.ListMap
import scala.concurrent.duration._

/**
 * Tests strategy based on [[TestSetup]] data
 */
object TestStrategy extends IOApp {
  import com.github.ppotseluev.algorate.tools.strategy.TestSetup._

  val done = new AtomicInteger()
  val started = new AtomicInteger()

  private val test = (share: Share, series: BarSeries) =>
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
//          println("per month statistics")
//          allStats.monthly.foreach { case (month, stats) =>
//            println(s"$month $stats")
//          }
          println()
          println(s"total: ${allStats.show}")
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
        s"${share.getTicker} (${share.getName}): ${stats.show}"
      }
      .mkString("\n")

  case class SectorsResults(
      sectorsStats: Map[String, Map[Share, TradingStats]]
  )

  object SectorsResults {
    implicit val show: Show[SectorsResults] = res =>
      res.sectorsStats.toList
        .sortBy(_._2.values.toList.map(_.totalPositions).max)
        .map { case (sector, value) =>
          val sorted: Map[Share, TradingStats] =
            ListMap.from(value.toList.sortBy(_._2.totalPositions))
          s"""
          |Sector: $sector
          |${sorted.show}
          |""".stripMargin
        }
        .mkString

    implicit val monoid: Monoid[SectorsResults] = semiauto.monoid

    def apply(share: Share, stats: TradingStats): SectorsResults = SectorsResults(
      Map(
        share.getSector -> Map(share -> stats)
      )
    )
  }
}
