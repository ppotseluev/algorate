package com.github.ppotseluev.algorate.ta4j
import org.ta4j.core.{BarSeries, BarSeriesManager, BaseStrategy}
import org.ta4j.core.analysis.criteria.{ReturnOverMaxDrawdownCriterion, VersusBuyAndHoldCriterion, WinningPositionsRatioCriterion}
import org.ta4j.core.analysis.criteria.pnl.GrossReturnCriterion
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.rules.{CrossedDownIndicatorRule, CrossedUpIndicatorRule, StopGainRule, StopLossRule}
import ta4jexamples.loaders.CsvTradesLoader

object Main extends App {



  // Getting a bar series (from any provider: CSV, web service, etc.)
  val series: BarSeries = CsvTradesLoader.loadBitstampSeries

  // Getting the close price of the bars
  val firstClosePrice = series.getBar(0).getClosePrice
  System.out.println("First close price: " + firstClosePrice.doubleValue)
  // Or within an indicator:
  val closePrice = new ClosePriceIndicator(series)
  // Here is the same close price:
  System.out.println(firstClosePrice.isEqual(closePrice.getValue(0))) // equal to firstClosePrice


  // Getting the simple moving average (SMA) of the close price over the last 5
  // bars
  val shortSma = new SMAIndicator(closePrice, 5)
  // Here is the 5-bars-SMA value at the 42nd index
  System.out.println("5-bars-SMA value at the 42nd index: " + shortSma.getValue(42).doubleValue)

  // Getting a longer SMA (e.g. over the 30 last bars)
  val longSma = new SMAIndicator(closePrice, 30)

  // Ok, now let's building our trading rules!

  // Buying rules
  // We want to buy:
  // - if the 5-bars SMA crosses over 30-bars SMA
  // - or if the price goes below a defined price (e.g $800.00)
  val buyingRule = new CrossedUpIndicatorRule(shortSma, longSma).or(new CrossedDownIndicatorRule(closePrice, 800))

  // Selling rules
  // We want to sell:
  // - if the 5-bars SMA crosses under 30-bars SMA
  // - or if the price loses more than 3%
  // - or if the price earns more than 2%
  val sellingRule = new CrossedDownIndicatorRule(shortSma, longSma).or(new StopLossRule(closePrice, series.numOf(3))).or(new StopGainRule(closePrice, series.numOf(2)))

  // Running our juicy trading strategy...
  val seriesManager = new BarSeriesManager(series)
  val tradingRecord = seriesManager.run(new BaseStrategy(buyingRule, sellingRule))
  System.out.println("Number of positions for our strategy: " + tradingRecord.getPositionCount)
  println(s"Is closed: ${tradingRecord.isClosed}")

  // Analysis



  // Getting the winning positions ratio
  val winningPositionsRatio = new WinningPositionsRatioCriterion
  System.out.println("Winning positions ratio: " + winningPositionsRatio.calculate(series, tradingRecord))
  // Getting a risk-reward ratio
  val romad = new ReturnOverMaxDrawdownCriterion
  System.out.println("Return over Max Drawdown: " + romad.calculate(series, tradingRecord))

  // Total return of our strategy vs total return of a buy-and-hold strategy
  val vsBuyAndHold = new VersusBuyAndHoldCriterion(new GrossReturnCriterion)
  System.out.println("Our return vs buy-and-hold return: " + vsBuyAndHold.calculate(series, tradingRecord))
}
