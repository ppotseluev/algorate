package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.tools.backtesting.BarSeriesProvider
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.num.Num
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

object CorrelationAnalyzer extends App {

  // Set the list of crypto coins you want to analyze
  val coinNames: List[String] = List(
    "FUN",
    "HBAR",
    "MFT",
    "ARPA",
    "ICX",
    "FET",
    "FIL",
    "ADA",
    "NKN",
    "STX",
    "WAN",
    "MITH",
    "QTUM",
    "COS",
    "BEAM",
    "DOT",
    "BNB",
    "BCH",
    "BAT",
    "MTL",
    "OMG",
    "BTT",
    "DASH",
    "ONE",
    "TROY",
    "RLC",
    "ZIL",
    "ALGO",
    "TRX",
    "LTC",
    "KAVA",
    "XRP",
    "CTXC",
    "KEY",
    "XTZ",
    "AVAX",
    "TFUEL",
    "MKR",
    "BAND",
    "HOT",
    "DOGE",
    "LINK",
    "REN",
    "KSM",
    "CVC",
    "WAVES",
    "TCT",
    "DUSK",
    "COCOS",
    "PERL",
    "MATIC",
    "XMR",
    "BTC",
    "DENT",
    "RVN",
    "BNT",
    "UNI",
    "FTT",
    "IOST",
    "THETA",
    "NANO",
    "ZRX",
    "IOTX",
    "TOMO",
    "ONT",
    "CELR",
    "DREP",
    "ETH",
    "NEO",
    "NULS",
    "ANKR",
    "ATOM",
    "ZEC",
    "ETC",
    "GTO",
    "SOL",
    "XLM",
    "OGN",
    "DOCK",
    "AAVE",
    "CHZ",
    "ENJ",
    "WIN",
    "VITE",
    "IOTA",
    "VET",
    "FTM",
    "EOS",
    "ONG"
  )

  // Set the correlation threshold to identify duplicates
  val correlationThreshold: Double = 0.9
  //  val period = 100

  // Set the downsampling period for the moving average
//  val downsamplingPeriod: Int = 10

  val done = new AtomicInteger()

  def removeDuplicateCoins(
      coins: List[String],
      threshold: Double
//      downsamplingPeriod: Int
  ): List[String] = {
    val n = coins.length
    val duplicates = scala.collection.mutable.Set[String]()

    // Limit the number of parallel threads
    val maxThreads = 8
    implicit val limitedExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(maxThreads)
    )

    // Use Future.sequence to run tasks in parallel with a limited number of threads
    val tasks = for (i <- 0 until n) yield Future {
      val coinA = coins(i)
      if (!duplicates.contains(coinA)) {
        val seriesA = load(coinA).unsafeRunSync()
        for (j <- i + 1 until n) {
          val coinB = coins(j)
          if (!duplicates.contains(coinB)) {
            val seriesB = load(coinB).unsafeRunSync()
            val correlations =
              seriesA.zip(seriesB).map((calculateCorrelation(coinA, coinB) _).tupled)
            if (
              correlations.forall(correlation => !correlation.isNaN && correlation >= threshold)
            ) {
              println(s"$coinB correlates with $coinA, $correlations. Count $coinB as duplicate.")
              duplicates.synchronized {
                duplicates += coinB
              }
            }
          }
        }
      }
      println(s"Done: ${done.incrementAndGet()}")
    }

    // Wait for all tasks to complete
    val _ = Await.result(Future.sequence(tasks), Duration.Inf)

    println("duplicates")
    println(duplicates)
    coins.diff(duplicates.toList)
  }

  def calculateCorrelation(
      coin1: String,
      coin2: String
  )(series1: BarSeries, series2: BarSeries): Double = {
    require(series1 != null && series2 != null, "Both BarSeries must be non-null")
    if (series1.getBarCount == series2.getBarCount) {

      val closePrices1 = series1.getBarData.asScala.map(_.getClosePrice.doubleValue())
      val closePrices2 = series2.getBarData.asScala.map(_.getClosePrice.doubleValue())

      val correlation = new PearsonsCorrelation()
      correlation.correlation(closePrices1.toArray, closePrices2.toArray)
    } else {
      println(s"${series1.getBarCount} ($coin1) != ${series2.getBarCount} ($coin2), skipping")
      Double.NaN
    }
  }

  class DownsampledIndicator(val indicator: Indicator[Num], val downsamplingPeriod: Int)
      extends AbstractIndicator[Num](indicator.getBarSeries) {
    override def getValue(index: Int): Num = {
      if (index < 0 || index * downsamplingPeriod >= indicator.getBarSeries.getBarCount) {
        throw new IndexOutOfBoundsException(s"Index: $index")
      }
      indicator.getValue(index * downsamplingPeriod)
    }
  }

  val dataProvider = new BarSeriesProvider(Factory.io.archive)

  def load(coinName: String): IO[List[BarSeries]] = {
    val asset = TradingAsset.crypto(coinName)
    val intervals = List(
      CandlesInterval(
        interval = DaysInterval(
          LocalDate.of(2021, 1, 28),
          LocalDate.of(2021, 1, 28)
        ),
        resolution = OneMinute
      ),
      CandlesInterval(
        interval = DaysInterval(
          LocalDate.of(2021, 6, 10),
          LocalDate.of(2021, 6, 10)
        ),
        resolution = OneMinute
      ),
      CandlesInterval(
        interval = DaysInterval(
          LocalDate.of(2021, 11, 2),
          LocalDate.of(2021, 11, 2)
        ),
        resolution = OneMinute
      )
    )
    intervals.traverse(dataProvider.getBarSeries(asset, _))
  }

  val filteredCoins = removeDuplicateCoins(coinNames, correlationThreshold)
  println(s"Filtered coins: $filteredCoins")
  println("")
  System.exit(0)
}
