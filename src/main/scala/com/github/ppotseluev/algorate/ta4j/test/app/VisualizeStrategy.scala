package com.github.ppotseluev.algorate.ta4j.test.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.github.ppotseluev.algorate.core.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.core.Broker.DaysInterval
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.BarSeriesProvider
import com.github.ppotseluev.algorate.ta4j.Charts
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester
import com.softwaremill.tagging.Tagger
import com.typesafe.scalalogging.StrictLogging
import java.time.LocalDate

object VisualizeStrategy extends IOApp with StrictLogging {

  val strategy = Strategies.intraChannel
  val tester = new StrategyTester(strategy)
  //  val ticker = "YNDX".taggedWith[Tags.Ticker]
  //  val ticker = "CHMF".taggedWith[Tags.Ticker]
  val ticker = "MA".taggedWith[Tags.Ticker]
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2022, 2, 1),
      LocalDate.of(2022, 7, 24)
    ),
    resolution = OneMinute
  )

  override def run(args: List[String]): IO[ExitCode] = {
    Factory
      .tinkoffBroker[IO](
        token = args.head,
        accountId = "fake_acc_id".taggedWith[Tags.BrokerAccountId]
      )
      .use { broker =>
        val seriesProvider = new BarSeriesProvider[IO](broker)
        for {
          share <- broker.getShare(ticker)
          series <- seriesProvider.getBarSeries(share, interval)
          _ <- IO {
            logger.info(s"Data has been collected (${series.getBarCount} bars), start testing...")
          }
          result = tester.test(series)
          _ <- IO {
            println(result)
            Charts.display(
              strategyBuilder = strategy,
              series = series,
              tradingStats = Some(result),
              title = ticker
            )
          }
        } yield ExitCode.Success
      }

  }
}
