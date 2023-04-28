package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.tools.backtesting.Assets
import com.github.ppotseluev.algorate.tools.backtesting.Assets.Sampler.SampleSize
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.Period
import com.github.ppotseluev.algorate.tools.backtesting.Testkit

import java.util.concurrent.atomic.AtomicInteger
import org.ta4j.core.BarSeries

object SamplingTester extends IOApp.Simple {
  val periods: List[Period] = (2020 to 2020).toList.map(Period(_)).flatMap(_.splitMonthly)
  val assets: List[TradingAsset] = Assets.shares
  val depth: Int = 10
  val threshold = 1.05
  val assetsSampleSize = 5
  val periodsSampleSize = 5

  private val strategies: Map[String, BarSeries => FullStrategy] = Map(
    "current" -> Strategies.createDefault(Params(50, 0.0008, 0.3, 0.01, 10)),
    "intraChannel" -> Strategies.intraChannel,
    "channelBreakdown" -> Strategies.channelBreakdown,
    "random" -> Strategies.random()
  )

  val done = new AtomicInteger()
  val testkit = new Testkit[IO](logProgress = false)

  def profitRatio(
      periods: List[Period],
      sample: List[TradingAsset]
  )(implicit strategy: BarSeries => FullStrategy): IO[Double] =
    testkit
      .test(periods.map(_.toInterval), sample)
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