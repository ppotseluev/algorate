package com.github.ppotseluev.algorate.tools.strategy.app.backtesting

import com.github.ppotseluev.algorate.{Currency, Money}
import org.ta4j.core.BarSeries

class TrainTestValidate {}

object TrainTestValidate {
  case class Datasets(train: BarSeries, validation: BarSeries, test: BarSeries)

  case class PerformanceMetrics(
      profitRatio: Double,
      totalProfit: Double
  )

  def splitData(
      series: BarSeries,
      trainRatio: Double = 0.6,
      validateRatio: Double = 0.2
  ): Datasets = {
    val trainSize = (series.getBarCount * trainRatio).toInt
    val validateSize = (series.getBarCount * validateRatio).toInt

    val trainSeries = series.getSubSeries(0, trainSize)
    val validateSeries = series.getSubSeries(trainSize, trainSize + validateSize)
    val testSeries = series.getSubSeries(trainSize + validateSize, series.getBarCount)

    Datasets(trainSeries, validateSeries, testSeries)
  }

}
