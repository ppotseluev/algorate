package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.derived.semiauto
import cats.effect.{IO, IOApp}
import cats.implicits._
import cats.{Monoid, Show}
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.{CandleResolution, CandlesInterval}
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.strategy.{Strategies, StrategyBuilder}
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.{Period, Testkit}
import com.github.ppotseluev.algorate.tools.backtesting.optimizer.OptimizationToolkit.PerformanceMetrics
import enumeratum._

import java.util.concurrent.atomic.AtomicInteger
import scala.math.Ordered.orderingToOrdered
import scala.util.Random

object StrategyAssessor extends IOApp.Simple {
  //total number of tests = periods.size * samplesCount * 2 (strategies count)

  val samplesCount = 10
  val intervals: List[CandlesInterval] = List(
//    Period.firstHalf(2021),
//    Period.secondHalf(2021),
    Period.firstHalf(2021)
//    Period.secondHalf(2022)
  ).map(_.toCandlesInterval(CandleResolution.FiveMinute))

  val assets: List[TradingAsset] = {
    implicit val sampler: Sampler = Sampler.SampleSize(400, seed = 123000L.some)
    (
      shares.sample ++
        cryptocurrencies.sample ++
        allCryptocurrencies.sample
    ).sample
  }

  private val strategies: StrategiesSet[StrategyBuilder] = StrategiesSet(
    defaultStrategy = Strategies.default,
    featureStrategy = Strategies.createDefault(Params(enableFeature = true))
  )

  private val performanceComparators = PerformanceComparator.valueMap

  val done = new AtomicInteger()
  val testkit = new Testkit[IO](logProgress = false, skipNotFound = true)

  private def score(
      results: StrategiesSet[PerformanceMetrics]
  )(implicit pc: PerformanceComparator): StrategiesSet[Score] =
    if (results.defaultStrategy > results.featureStrategy) StrategiesSet(1, 0)
    else if (results.featureStrategy > results.defaultStrategy) StrategiesSet(0, 1)
    else StrategiesSet(0, 0)

  override def run: IO[Unit] = {
    val seed = Random.nextLong()
    def sampler(i: Int) = Sampler.KFold(
      k = samplesCount,
      select = i.some,
      seed = seed.some
    )
    val samples = (0 until samplesCount)
      .map(sampler)
      .map { implicit s =>
        assets.sample
      }
      .toList
    val source = for {
      sample <- samples
      interval <- intervals
    } yield (sample, interval)
    source
      .traverse { case (sample, interval) =>
        testkit
          .batchTest(interval, sample)(strategies.toList)
          .map(_.map(PerformanceMetrics.fromResults))
          .map(StrategiesSet.fromList)
      }
      .map { results =>
        performanceComparators.view.mapValues { implicit pc =>
          results.foldMap(score)
        }.toMap
      }
      .flatMap(printResults)
  }

  private def printResults(results: Map[ComparatorName, StrategiesSet[Score]]): IO[Unit] = IO {
    println("feature vs default")
    results.foreach { case (cmpName, value) =>
      println(s"$cmpName ${value.show}")
    }
  }

  sealed abstract class PerformanceComparator(ord: Ordering[PerformanceMetrics])
      extends EnumEntry
      with Ordering[PerformanceMetrics] {
    override def toString: String = getClass.getSimpleName.filter(_ != '$')
    override def compare(x: PerformanceMetrics, y: PerformanceMetrics): Score =
      ord.compare(x, y)
  }
  object PerformanceComparator extends Enum[PerformanceComparator] {
    object ByProfitRatio extends PerformanceComparator(Ordering.by(_.realProfitRatio))
    object ByProfit extends PerformanceComparator(Ordering.by(_.realProfit))
    object ByWinRatio extends PerformanceComparator(Ordering.by(_.realWinRatio))
    object ByNumberOfTrades extends PerformanceComparator(Ordering.by(_.numberOfTrades))

    override def values: IndexedSeq[PerformanceComparator] = findValues
    def valueMap: Map[String, PerformanceComparator] = values.map { cmp =>
      cmp.toString -> cmp
    }.toMap
  }

  case class StrategiesSet[T](
      defaultStrategy: T,
      featureStrategy: T
  ) {
    def toList: List[T] = List(defaultStrategy, featureStrategy)
  }
  object StrategiesSet {
    implicit def monoid[T: Monoid]: Monoid[StrategiesSet[T]] = semiauto.monoid
    implicit val showScore: Show[StrategiesSet[Score]] = Show.show { x =>
      s"${x.featureStrategy}:${x.defaultStrategy}"
    }

    def fromList[T](source: List[T]): StrategiesSet[T] = source match {
      case x :: y :: Nil => StrategiesSet(x, y)
      case other         => throw new IllegalAccessException(s"Incorrect input $other")
    }
  }

  type ComparatorName = String
  type Score = Int
}
