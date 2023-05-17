package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.strategy.StrategyBuilder
import com.github.ppotseluev.algorate.tools.backtesting.Assets
import com.github.ppotseluev.algorate.tools.backtesting.Assets.Sampler.SampleSize
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.Period
import com.github.ppotseluev.algorate.tools.backtesting.Testkit
import java.util.concurrent.atomic.AtomicInteger

object SamplingTester extends IOApp.Simple {
  val candlesResolution: CandleResolution = CandleResolution.FiveMinute
  val periods: List[Period] = (2021 to 2021).toList.map(Period(_)).flatMap(_.splitMonthly)
  val assets: List[TradingAsset] = Assets.shares
  val depth: Int = 10
  val threshold = 1.05
  val assetsSampleSize = 100
  val periodsSampleSize = 6

  private val strategies: Map[String, StrategyBuilder] = Map(
    "current" -> Strategies.default,
    "feature" -> Strategies.createDefault(Params(enableFeature = true)),
    "intraChannel" -> Strategies.intraChannel
//    "channelBreakdown" -> Strategies.channelBreakdown,
//    "random" -> Strategies.random()
  )

  val done = new AtomicInteger()
  val testkit = new Testkit[IO](logProgress = false)

  def profitRatio(
      periods: List[Period],
      sample: List[TradingAsset]
  )(implicit strategy: StrategyBuilder): IO[Double] =
    testkit
      .test(periods.map(_.toCandlesInterval(candlesResolution)), sample)
      .map(
        _.aggregatedStats.profitRatio(false).values.sum
      ) //FIXME: it can cause sum of different currencies

  override def run: IO[Unit] = (1 to depth).toList
    .traverse { _ =>
      val sample = assets.sample(SampleSize(assetsSampleSize))
      val periodsSample = periods.sample(SampleSize(periodsSampleSize))
      strategies.toList
        .traverse { case (name, strategy) =>
          profitRatio(periodsSample, sample)(strategy).map(name -> _)
        }
        .map(_.toMap)
        .map { r =>
          println(s"done: ${(done.incrementAndGet().toDouble * 100 / depth).toInt}%")
          r
        }
    }
    .map { result =>
      strategies.keySet.foreach { name =>
        val winRatios = result.map(_.apply(name))
        val profitable = winRatios.count(_ >= threshold)
        val percentage = 100 * profitable.toDouble / winRatios.size
        println(s"[$name] profitable factor: $percentage%")
      }
    }
}
