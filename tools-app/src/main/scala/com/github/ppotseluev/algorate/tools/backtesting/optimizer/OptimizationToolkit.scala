package com.github.ppotseluev.algorate.tools.backtesting.optimizer

import com.github.ppotseluev.algorate.strategy.Strategies.Params
import com.github.ppotseluev.algorate.tools.backtesting.SectorsResults

object OptimizationToolkit {
  def listParams(
      initialParams: Params, //todo make it generic
      paramsVariety: List[ParamVariety]
  ): List[Params] = {
    paramsVariety.foldLeft(List(initialParams)) { case (acc, paramVariety) =>
      for {
        params <- acc
        value <- paramVariety.listValues
      } yield paramVariety.set(params, value)
    }
  }

  case class ParamVariety(
      set: (Params, Double) => Params,
      paramRange: (Double, Double),
      paramStep: Double
  ) {
    def listValues: List[Double] =
      Iterator.iterate(paramRange._1)(_ + paramStep).takeWhile(_ <= paramRange._2).toList
  }

  case class PerformanceMetrics(
      realProfitRatio: Double,
      realProfit: Double
  )

  object PerformanceMetrics {
    implicit val ord: Ordering[PerformanceMetrics] = Ordering.by(_.realProfit)

    def fromResults(sectorsResults: SectorsResults): PerformanceMetrics = PerformanceMetrics(
      realProfit = sectorsResults.aggregatedStats.profit(fee = true).values.sum,
      realProfitRatio = sectorsResults.aggregatedStats.profitRatio(fee = true).values.sum
    )
  }
}
