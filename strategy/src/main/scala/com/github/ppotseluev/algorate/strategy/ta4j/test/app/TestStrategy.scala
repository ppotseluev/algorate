package com.github.ppotseluev.algorate.strategy.ta4j.test.app

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.ta4j.BarSeriesProvider
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester.TradingStats
import com.github.ppotseluev.algorate.ta4j.test.TestSetup
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.duration._

/**
 * Tests strategy based on [[TestSetup]] data
 */
object TestStrategy extends IOApp {
  import TestSetup._

  override def run(args: List[String]): IO[ExitCode] = {
    val token = args.head
    Factory
      .tinkoffBroker[IO](
        accountId = "fake_acc_id",
        investApi = InvestApi.create(token)
      )
      .use { broker =>
        val start = System.currentTimeMillis()

        val result: IO[SectorsResults] = {
          val barSeriesProvider = new BarSeriesProvider(broker)
          tickers
            .traverse(broker.getShare)
            .flatMap {
              _.parTraverse { share =>
                barSeriesProvider.getBarSeries(share, interval).map { series =>
                  val stats = StrategyTester(strategy).test(series)
                  SectorsResults(share, stats)
                }
              }
            }
            .map(_.combineAll)
        }
        result
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
