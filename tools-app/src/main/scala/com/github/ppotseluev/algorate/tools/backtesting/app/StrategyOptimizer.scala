package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.Stats
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.indicator._
import com.github.ppotseluev.algorate.tools.backtesting.BarSeriesProvider
import com.github.ppotseluev.algorate.tools.backtesting.StrategyTester
import java.time.LocalDate
import org.ta4j.core._
import org.ta4j.core.indicators._
import org.ta4j.core.indicators.helpers._
import org.ta4j.core.num._

object StrategyOptimizer extends IOApp.Simple {

  val assets: List[TradingAsset] = List(
    "STX"
  ).map(TradingAsset.crypto)

  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2020, 1, 1),
      LocalDate.of(2020, 12, 31)
    ),
    resolution = OneMinute
  )

  val strategy = Strategies.default

  def calculateIndicatorCorrelations(
      series: BarSeries,
      stats: Stats,
      indicators: List[Indicator[Num]]
  ): List[Double] = {
    val tradeOutcomes = extractTradeOutcomes(series, stats)
    indicators.map { indicator =>
      val indicatorValues = extractIndicatorValues(series, stats, indicator)
      pearsonCorrelation(indicatorValues, tradeOutcomes)
    }
  }

  def extractTradeOutcomes(series: BarSeries, stats: Stats): List[Double] =
    stats.positions.map { trade =>
      if (trade.hasProfit) 1.0 else 0.0
    }.toList

  def extractIndicatorValues(
      series: BarSeries,
      stats: Stats,
      indicator: Indicator[_ <: Num]
  ): List[Double] =
    stats.positions.map { trade =>
      val entryIndex = trade.getEntry.getIndex
      indicator.getValue(entryIndex).doubleValue()
    }.toList

  def pearsonCorrelation(xs: List[Double], ys: List[Double]): Double = {
    require(xs.length == ys.length)

    val n = xs.length
    val xMean = xs.sum / n
    val yMean = ys.sum / n

    val num = xs.zip(ys).map { case (x, y) => (x - xMean) * (y - yMean) }.sum
    val den = math.sqrt(xs.map(x => math.pow(x - xMean, 2)).sum) * math.sqrt(
      ys.map(y => math.pow(y - yMean, 2)).sum
    )

    num / den
  }

  override def run: IO[Unit] =
    assets.parTraverse(test).void

  private def test(asset: TradingAsset): IO[Unit] =
    for {
      series <- new BarSeriesProvider[IO](Factory.io.archive).getBarSeries(asset, interval)
      tradingStats <- StrategyTester[IO](strategy).test(series, asset)
    } yield {
      val closePriceIndicator = new ClosePriceIndicator(series)

      val stochasticOscillatorK = new StochasticOscillatorKIndicator(
        series,
        30
      )

      def num = series.numOf _

      val macd = new MACDIndicator(closePriceIndicator)

      val indicators = List(
        new VolumeIndicator(series),
        (new TradeCountIndicator(series): AbstractIndicator[java.lang.Long]).map(num)
      )

      val results = Map(
        "short" -> tradingStats.short,
        "long" -> tradingStats.long
      ).map { case (name, stats) =>
        name -> indicators.zip(calculateIndicatorCorrelations(series, stats, indicators))
      }
      synchronized {
        println(s"${asset.ticker} results")
        results.foreach { case (name, correlations) =>
          println(s"[$name] correlations between trade outcomes and indicators:")
          for ((indicator, correlation) <- correlations) {
            println(s" - ${indicator.getClass.getSimpleName}: $correlation")
          }
        }
        println()
      }
    }
}
