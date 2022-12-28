package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
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
import ru.tinkoff.piapi.core.InvestApi

object VisualizeStrategy extends IOApp with StrictLogging {

  val strategy = Strategies.intraChannel
  val tester = new StrategyTester(strategy)
  //  val ticker = "YNDX"
  //  val ticker = "CHMF"
  val ticker = "CSCO"
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.now.minusDays(5),
      LocalDate.now.minusDays(2)
    ),
    resolution = OneMinute
  )

  override def run(args: List[String]): IO[ExitCode] = {
    val token = args.head
    Factory
      .tinkoffBroker[IO](
        accountId = "fake_acc_id",
        investApi = InvestApi.create(token)
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
            TradingCharts.display(
              strategyBuilder = strategy,
              series = series,
              tradingStats = Some(result),
              title = s"$ticker (${share.getName})"
            )
          }
        } yield ExitCode.Success
      }

  }
}
