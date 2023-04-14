package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Ticker
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.strategy.StrategyTester
import com.github.ppotseluev.algorate.tools.strategy.TestSetup
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
import com.typesafe.scalalogging.StrictLogging
import java.time.LocalDate

object VisualizeStrategy extends IOApp with StrictLogging {
//TODO check discrepancy when use archive data
  val strategy = Strategies.intraChannel
  val policy = TestSetup.fixedTradeCostPolicy().andThen(_.allowedOrElse(Decision.Allowed(1)))
  val tester = StrategyTester(strategy, policy)
  val id: Either[Ticker, InstrumentId] = "DOW".asLeft
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2021, 1, 1),
      LocalDate.of(2021, 12, 31)
    ),
    resolution = OneMinute
  )

  override def run(args: List[String]): IO[ExitCode] = {
    Factory.io.tinkoffBroker
      .use { broker =>
        val seriesProvider = new BarSeriesProvider[IO](broker)
        for {
          share <- id.fold(broker.getShareByTicker, broker.getShareById)
          asset = TradingAsset(
            instrumentId = share.getFigi,
            ticker = share.getTicker,
            currency = share.getCurrency
          )
          series <- seriesProvider.getBarSeries(asset, interval)
          _ <- IO {
            logger.info(s"Data has been collected (${series.getBarCount} bars), start testing...")
          }
          result = tester.test(series, asset)
          _ <- IO {
            println("per month statistics")
            result.monthly.foreach { case (month, stats) =>
              println(s"$month ${stats.show}")
            }
            println(result.show)
            TradingCharts.display(
              strategyBuilder = strategy,
              series = series,
              tradingStats = Some(result),
              title = s"${share.getTicker} (${share.getName})",
              profitableTradesFilter = true.some
            )
          }
        } yield ExitCode.Success
      }

  }
}
