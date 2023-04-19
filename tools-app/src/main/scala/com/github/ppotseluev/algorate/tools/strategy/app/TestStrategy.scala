package com.github.ppotseluev.algorate.tools.strategy.app

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester
import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries
import scala.collection.immutable.ListMap
import scala.concurrent.duration._

/**
 * Tests strategy based on [[TestSetup]] data
 */
object TestStrategy extends IOApp {
  import com.github.ppotseluev.algorate.tools.strategy.TestSetup._

  val done = new AtomicInteger()

  private val test = (asset: TradingAsset, series: BarSeries) =>
    StrategyTester[IO](strategy).test(series, asset).map { stats =>
      val results = SectorsResults(asset, stats)
      println(s"done: ${done.incrementAndGet()}")
      results
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val factory = Factory.io
    factory.tinkoffBroker.use { broker =>
      val start = System.currentTimeMillis()
      val barSeriesProvider = new BarSeriesProvider(broker)

      def testAll(assets: List[TradingAsset]): IO[SectorsResults] = {
        val maxConcurrent = 8
        println("start testing")
        barSeriesProvider
          .streamBarSeries(assets, interval, maxConcurrent, skipNotFound = true)
          .parEvalMapUnordered(maxConcurrent)(test.tupled)
          .compile
          .toList
          .map(_.combineAll)
      }

      testAll(factory.config.assets)
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

  implicit val tickersShow: Show[Map[TradingAsset, TradingStats]] =
    (stats: Map[TradingAsset, TradingStats]) =>
      stats
        .map { case (asset, stats) =>
          s"${asset.ticker}: ${stats.show}"
        }
        .mkString("\n")

  case class SectorsResults(
      sectorsStats: Map[String, Map[TradingAsset, TradingStats]]
  ) {
    def flatten: Map[TradingAsset, TradingStats] = sectorsStats.flatMap(_._2)
  }

  object SectorsResults {
    implicit val show: Show[SectorsResults] = res =>
      res.sectorsStats.toList
        .sortBy(_._2.values.toList.map(_.totalPositions).max)
        .map { case (sector, value) =>
          val sorted: Map[TradingAsset, TradingStats] =
            ListMap.from(value.toList.sortBy(_._2.totalPositions))
          s"""
          |Sector: $sector
          |${sorted.show}
          |""".stripMargin
        }
        .mkString

    implicit val monoid: Monoid[SectorsResults] = semiauto.monoid

    def apply(asset: TradingAsset, stats: TradingStats): SectorsResults = SectorsResults(
      Map(
        asset.sector -> Map(asset -> stats)
      )
    )
  }
}
