package com.github.ppotseluev.algorate.tools.strategy.app

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.{CandlesInterval, DaysInterval}
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.tools.strategy.BarSeriesProvider
import org.ta4j.core._
import org.ta4j.core.indicators.{AbstractIndicator, SMAIndicator}
import org.ta4j.core.indicators.helpers._
import org.ta4j.core.indicators.statistics._
import org.ta4j.core.num._

import scala.collection.parallel.CollectionConverters._
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.parallel.immutable.ParSeq
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object CorrelationAnalyzer {

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

  // Set the downsampling period for the moving average
  val downsamplingPeriod: Int = 10

  def main(args: Array[String]): Unit = {
    val filteredCoins = removeDuplicateCoins(coinNames, correlationThreshold, downsamplingPeriod)
    println(s"Filtered coins: $filteredCoins")
  }
  val done = new AtomicInteger()

  def removeDuplicateCoins(coins: List[String], threshold: Double, downsamplingPeriod: Int): List[String] = {
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
            val correlation = calculatePearsonCorrelation(seriesA, seriesB, downsamplingPeriod)
            if (!correlation.isNaN && correlation >= threshold) {
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

    coins.diff(duplicates.toList)
  }

  def calculatePearsonCorrelation(seriesA: BarSeries, seriesB: BarSeries, downsamplingPeriod: Int): Double = {
    val minBarCount = math.min(seriesA.getBarCount, seriesB.getBarCount)

    // Check if the BarSeries has enough bars for the downsampling period
    if (minBarCount < downsamplingPeriod) {
      return Double.NaN
    }

    val closePriceA = new ClosePriceIndicator(seriesA)
    val closePriceB = new ClosePriceIndicator(seriesB)

    val downsampledClosePriceA = new DownsampledIndicator(closePriceA, downsamplingPeriod)
    val downsampledClosePriceB = new DownsampledIndicator(closePriceB, downsamplingPeriod)

    val minEndIndex = math.min(seriesA.getEndIndex, seriesB.getEndIndex)

    val pearsonCorrelation = new PearsonCorrelationIndicator(downsampledClosePriceA, downsampledClosePriceB, downsamplingPeriod)
    pearsonCorrelation.getValue(minEndIndex / downsamplingPeriod).doubleValue()
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

  // Replace this with your load function that reads BarSeries from CSV files
  def load(coinName: String): IO[BarSeries] = {
    val asset = TradingAsset.crypto(coinName)
    val interval = CandlesInterval(
      interval = DaysInterval(
        LocalDate.of(2022, 12, 1),
        LocalDate.of(2022, 12, 31)
      ),
      resolution = OneMinute
    )
    dataProvider.getBarSeries(asset, interval)
  }
}
