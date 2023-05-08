package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.math.PrettyDuration.PrettyPrintableDuration
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.tools.backtesting.{BarSeriesProvider, Period, StrategyTester}
import com.typesafe.scalalogging.StrictLogging

import java.time.LocalDate
import scala.concurrent.duration._

object VisualizeStrategy extends IOApp with StrictLogging {
  val interval = Period.firstHalf(2022).toCandlesInterval(CandleResolution.FiveMinute)
  val strategy = Strategies.createDefault(
    CurrentStrategy.params //.copy(maxError = 0.007)
  )
//    Strategies.createDefault(Params(50, 0.01, 0.6, 0.02, 10))
  val visualize = true
  val tester = StrategyTester[IO](
    strategy,
    maxParallelism = if (visualize) 1 else 8
  )
  val asset: TradingAsset = TradingAsset.crypto("HOT")

  override def run(args: List[String]): IO[ExitCode] = {
    val archive = Factory.io.archive
    val seriesProvider = new BarSeriesProvider[IO](archive)
    for {
      assetData <- seriesProvider.getBarSeries(asset, interval)
      series = assetData.barSeries
      _ <- IO {
        logger.info(s"Data has been collected (${series.getBarCount} bars), start testing...")
      }
      start = System.currentTimeMillis()
      result <- tester.test(assetData)
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
            assetData = assetData,
            tradingStats = Some(result),
            title = s"${asset.ticker}",
            profitableTradesFilter = none
          )
        }
      }
    } yield ExitCode.Success
  }
}
