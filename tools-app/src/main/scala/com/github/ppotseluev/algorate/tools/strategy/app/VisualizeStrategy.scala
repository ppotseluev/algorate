package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits._
import cats.effect.IOApp
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
//TODO check discrepancy when use archive data
  val strategy = Strategies.intraChannel
  val tester = StrategyTester(strategy)
  //  val ticker = "YNDX"
  //  val ticker = "CHMF"
  val id = "BBG000BBS2Y0"
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2022, 6, 8),
      LocalDate.of(2022, 6, 8)
    ),
    resolution = OneMinute
  )

  override def run(args: List[String]): IO[ExitCode] = {
    Factory.io.tinkoffBroker
      .use { broker =>
        val seriesProvider = new BarSeriesProvider[IO](broker)
        for {
          share <- broker.getShareById(id)
          series <- seriesProvider.getBarSeries(share, interval)
          _ <- IO {
            logger.info(s"Data has been collected (${series.getBarCount} bars), start testing...")
          }
          asset = TradingAsset(
            ticker = share.getTicker,
            currency = share.getCurrency
          )
          result = tester.test(series, asset)
          _ <- IO {
            println(result.show)
            TradingCharts.display(
              strategyBuilder = strategy,
              series = series,
              tradingStats = Some(result),
              title = s"${share.getTicker} (${share.getName})"
            )
          }
        } yield ExitCode.Success
      }

  }
}
