package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.{CandleResolution, CandlesInterval, DaysInterval}
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.tools.backtesting.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.backtesting.StrategyTester
import com.typesafe.scalalogging.StrictLogging

import java.time.LocalDate
import scala.concurrent.duration._

object VisualizeStrategy extends IOApp with StrictLogging {
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2022, 1, 1),
      LocalDate.of(2022, 12, 31)
    ),
    resolution = CandleResolution.OneMinute
  )
  val params =
//    Params(60, 0.0028000000000000004, 0.6, 0.007, 60)
    Params(50, 0.0008, 0.3, 0.01, 10)
  val strategy = Strategies.createDefault(params)
  val visualize = true
  val tester = StrategyTester[IO](
    strategy,
    maxParallelism = if (visualize) 1 else 8
  )
  val asset: TradingAsset = TradingAsset.share("BBG00CWTTQ41")

  override def run(args: List[String]): IO[ExitCode] = {
    Factory.io.tinkoffBroker
      .use { broker =>
        val seriesProvider = new BarSeriesProvider[IO](broker)
        for {
          series <- seriesProvider.getBarSeries(asset, interval)
          _ <- IO {
            logger.info(s"Data has been collected (${series.getBarCount} bars), start testing...")
          }
          start = System.currentTimeMillis()
          result <- tester.test(series, asset)
          end = System.currentTimeMillis()
          _ <- IO {
            println("per month statistics")
            result.monthly.toSeq
              .sortBy { case (_, stats) =>
                stats.profit(fee = false).values.sum
              }
              .foreach { case (month, stats) =>
                println(s"$month ${stats.show}")
              }
            println(result.show)
            println(s"Testing took ${(end - start).millis.pretty}")
            if (visualize) {
              TradingCharts.display(
                strategyBuilder = strategy,
                series = series,
                tradingStats = Some(result),
                title = s"${asset.ticker}",
                profitableTradesFilter = none
              )
            }
          }
        } yield ExitCode.Success
      }

  }
}
