package com.github.ppotseluev.algorate.ta4j.test.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import com.github.ppotseluev.algorate.core.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.core.Broker.{CandlesInterval, Interval}
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.BarSeriesProvider
import com.github.ppotseluev.algorate.ta4j.Charts
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.github.ppotseluev.algorate.ta4j.test.StrategyTester
import com.softwaremill.tagging.Tagger
import org.jfree.data.time.Day

import java.time.ZoneId

object VisualizeStrategy extends IOApp {

  val strategy = Strategies.test
  val tester = new StrategyTester(strategy)
  //  val ticker = "YNDX".taggedWith[Tags.Ticker]
  //  val ticker = "CHMF".taggedWith[Tags.Ticker]
  val ticker = "POLY".taggedWith[Tags.Ticker]
  val interval = CandlesInterval(
    interval = Interval(
      new Day(9, 2, 2021),
      new Day(22, 2, 2021)
    ),
    resolution = OneMinute,
    zoneId = ZoneId.of("+03:00")
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
