package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.Period
import com.github.ppotseluev.algorate.tools.backtesting.Testkit
import com.github.ppotseluev.algorate.tools.backtesting.optimizer.OptimizationToolkit._
import java.io.PrintWriter

object StrategyParametersFitter extends IOApp.Simple {

  val interval = Period(2021).toCandlesInterval(
    CandleResolution.FiveMinute
  )
  implicit val sampler: Sampler = Sampler //.SampleSize(100, seed = 0L.some)
    .KFold(
      k = 4,
      select = 1.some,
      seed = 106L.some
    )
  val assets: List[TradingAsset] = cryptocurrencies.sample

  val testkit = new Testkit[IO]()

  override def run: IO[Unit] = {
    val paramsVariety = List(
//      ParamVariety(
//        set = (params, windowSize) => params.copy(extremumWindowSize = windowSize.toInt),
//        paramRange = 60d -> 100d,
//        paramStep = 5
//      ),
      ParamVariety(
        set = (params, x) => params.copy(minPotentialChange = x),
        paramRange = 0.01 -> 0.03,
        paramStep = 0.001
      )
//      ParamVariety(
//        set = (params, x) => params.copy(maxError = x),
//        paramRange = 0.004 -> 0.0085,
//        paramStep = 0.0005
//      )
//      ParamVariety(
//        set = (params, x) => params.copy(maxParallelDelta = x),
//        paramRange = 0.3 -> 1.05,
//        paramStep = 0.05
//      )
//      ParamVariety(
//        set = (params, x) => params.copy(shortMacdPeriod = x.toInt),
//        paramRange = 10d -> 11d,
//        paramStep = 1
//      ),
//      ParamVariety(
//        set = (params, x) => params.copy(longMacdPeriod = x.toInt),
//        paramRange = 18d -> 21d,
//        paramStep = 2
//      ),
//      ParamVariety(
//        set = (params, x) => params.copy(macdSignalPeriod = x.toInt),
//        paramRange = 6d -> 8d,
//        paramStep = 1
//      )
//      ParamVariety(
//        set = (params, x) => params.copy(macdSignalPeriod = x.toInt),
//        paramRange = 5d -> 10d,
//        paramStep = 2
//      )
    )
    val initialParams = Params()
    val paramList = listParams(initialParams, paramsVariety).filter { params =>
      params.longMacdPeriod > params.shortMacdPeriod
    }
    println(s"all tests: ${paramList.length}")
    val strategies = paramList.map(Strategies.createDefault)
    testkit
      .batchTest(interval, assets)(strategies)
      .map(_.map(PerformanceMetrics.fromResults).zip(paramList).swapF)
      .map { result =>
        val report = result
          .sortBy(_._2)(PerformanceMetrics.ordByProfit)
          .map { case (params, metrics) =>
            val values = params.productIterator.toList ++ metrics.productIterator.toList
            values.mkString(",")
          }
          .mkString(sep = "\n")
          .replaceAll(",", ";")
          .replaceAll("\\.", ",")
        val printer = new PrintWriter("tools-app/data/results/fitter_results.csv")
        printer.println(report)
        printer.close()
      }
      .void
  }

}
