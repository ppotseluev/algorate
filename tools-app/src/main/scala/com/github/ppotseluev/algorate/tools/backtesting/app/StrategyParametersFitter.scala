package com.github.ppotseluev.algorate.tools.backtesting.app

import cats.implicits._
import cats.effect.{IO, IOApp, ParallelF}
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.tools.backtesting.{Period, SectorsResults, Testkit}
import com.github.ppotseluev.algorate.tools.backtesting.Assets._
import com.github.ppotseluev.algorate.tools.backtesting.optimizer.OptimizationToolkit._

object StrategyParametersFitter extends IOApp.Simple {

  val period: Period = Period(2021)
  implicit val sampler: Sampler = Sampler.SampleSize(100, seed = 100030L.some)
  val assets: List[TradingAsset] = shares.sample
//    List(
//    TradingAsset.crypto("ALGO"),
//    TradingAsset.crypto("BTC")
//  )

  val testkit = new Testkit[IO](maxConcurrentAssets = 8)

//  def test(
//      paramsList: List[Params]
//  ): IO[Map[Params, PerformanceMetrics]] =
//    paramsList
//      .parTraverse { params =>
//        test(params).map(params -> _)
//      }
//      .map(_.toMap)

  override def run: IO[Unit] = {
    val paramsVariety = List(
//      ParamVariety(
//        set = (params, maxError) => params.copy(maxError = maxError),
//        paramRange = 0.0008 -> 0.003,
//        paramStep = 0.0004
//      ),
//      ParamVariety(
//        set = (params, windowSize) =>
//          params.copy(
//            extremumWindowSize = windowSize.toInt,
//            shortMacdPeriod = windowSize.toInt
//          ),
//        paramRange = 10d -> 60d,
//        paramStep = 10
//      ),
//      ParamVariety(
//        set = (params, x) => params.copy(minPotentialChange = x),
//        paramRange = 0.005 -> 0.012,
//        paramStep = 0.002
//      )
      ParamVariety(
        set = (params, x) => params.copy(shortMacdPeriod = x.toInt),
        paramRange = 40d -> 60d,
        paramStep = 2
      )
    )
    val initialParams = Params(50, 0.0008, 0.6, 0.01, 10)
    val paramList = listParams(initialParams, paramsVariety)
    println(s"all tests: ${paramList.length}")
    val strategies = paramList.map(Strategies.createDefault)
    testkit
      .batchTest(period.toInterval, assets)(strategies)
      .map(_.map(PerformanceMetrics.fromResults).zip(paramList).swapF)
      .map { result =>
        val report = result.sortBy(_._2).mkString(sep = "\n")
        println(report)
      }
      .void
  }
}
