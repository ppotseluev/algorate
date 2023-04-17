package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester
import com.typesafe.scalalogging.StrictLogging
import java.time.LocalDate

object VisualizeStrategy extends IOApp with StrictLogging {
  val strategy = Strategies.intraChannel
  val tester = StrategyTester(strategy)
  val asset: TradingAsset = TradingAsset.crypto("BTCUSDT", "usdt")

//    ??? /// Either[Ticker, InstrumentId] = "DOW".asLeft
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2020, 1, 1),
      LocalDate.of(2020, 12, 31)
    ),
    resolution = OneMinute
  )

  override def run(args: List[String]): IO[ExitCode] = {
    Factory.io.tinkoffBroker
      .use { broker =>
        val seriesProvider = new BarSeriesProvider[IO](broker)
        for {
          series <- seriesProvider.getBarSeries(asset, interval)
          _ <- IO {
            logger.info(s"Data has been collected (${series.getBarCount} bars), start testing...")
          }
          result = tester.test(series, asset)
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
            TradingCharts.display(
              strategyBuilder = strategy,
              series = series,
              tradingStats = Some(result),
              title = s"${asset.ticker}",
              profitableTradesFilter = none
            )
          }
        } yield ExitCode.Success
      }

  }
}
