package com.github.ppotseluev.algorate.strategy

import cats.implicits._
import com.github.ppotseluev.algorate.math.Approximator
import com.github.ppotseluev.algorate.strategy.FullStrategy.IndicatorInfo
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation.Points
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.strategy.indicator.ChannelUtils
import com.github.ppotseluev.algorate.strategy.indicator.HasDataIndicator
import com.github.ppotseluev.algorate.strategy.indicator.IndicatorSyntax
import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator
import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.strategy.indicator.VisualChannelIndicator
import org.ta4j.core.indicators.{
  AbstractIndicator,
  EMAIndicator,
  MACDIndicator,
  RSIIndicator,
  SMAIndicator
}
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import com.github.ppotseluev.algorate.strategy.indicator._
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseStrategy
import org.ta4j.core.Strategy
import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.bollinger.{
  BollingerBandsLowerIndicator,
  BollingerBandsMiddleIndicator
}
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.DifferenceIndicator
import org.ta4j.core.indicators.helpers.SumIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import org.ta4j.core.rules._

import scala.concurrent.duration._

object Strategies {
  val doNothing: Strategy = new BaseStrategy(
    BooleanRule.FALSE,
    BooleanRule.FALSE
  )

  def random(
      enterChance: Double,
      exitChance: Double
  ): BarSeries => FullStrategy = barSeries => {
    def rule(chance: Double) = new AbstractRule {
      override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
        math.random() < chance && barSeries.getBarCount > 30
    }
    val strategy = new BaseStrategy(
      rule(enterChance),
      rule(exitChance)
    )
    FullStrategy(
      strategy,
      strategy,
      () => Map("price" -> IndicatorInfo(new ClosePriceIndicator(barSeries))),
      Map.empty
    )
  }

  val intraChannel: BarSeries => FullStrategy = implicit series => {
    def num(number: Number): Num =
      series.numOf(number)

    val closePrice = new ClosePriceIndicator(series)
    val volumeIndicator: AbstractIndicator[Num] = new VolumeIndicator(series)
    val relativeVolumeIndicator =
      new RelativeVolumeIndicator(series, lookbackPeriod = 7.days.toMinutes.toInt)
    val extremumWindowSize = 30
    val extremum: AbstractIndicator[Option[Extremum]] =
      LocalExtremumIndicator(closePrice, extremumWindowSize)
    val channel: AbstractIndicator[Option[Channel]] = ChannelIndicator(
      baseIndicator = closePrice,
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
      maxError = 0.0009 //0.0007 0.003🤔
    ).filter(ChannelUtils.isParallel(maxDelta = 0.6)) //todo?

    val rsi = new RSIIndicator(closePrice, 5)
    val volumeRsi = new RSIIndicator(volumeIndicator, 5)
    val volumeRule =
      new UnderIndicatorRule(volumeRsi, 60) &
        new OverIndicatorRule(volumeRsi, 40)

    val minPotentialChange = num(0.003)
    val maxK = Int.MaxValue

    val sma = new SMAIndicator(closePrice, 40)

    // Define MACD parameters
    val shortPeriod = 30
    val longPeriod = 60
    val signalPeriod = 20
    // Calculate the MACD line
    val macd = new MACDIndicator(closePrice, shortPeriod, longPeriod)

    // Calculate the signal line (an EMA of the MACD line)
    val macdEma = new EMAIndicator(macd, signalPeriod)

    val lowerBoundIndicator = channel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
    val upperBoundIndicator = channel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))

    val channelDiffIndicator: AbstractIndicator[Num] =
      new DifferenceIndicator(upperBoundIndicator, lowerBoundIndicator)
    val halfChannel = channelDiffIndicator.map(_.dividedBy(num(2)))
    val midChannelIndicator: AbstractIndicator[Num] =
      new SumIndicator(lowerBoundIndicator, halfChannel)

    val channelIsWideEnough =
      for {
        p <- closePrice: AbstractIndicator[Num]
        h <- halfChannel
      } yield h.dividedBy(p).isGreaterThan(minPotentialChange)

//    TODO return back? price may be too far from the bound at the entry moment, need to prevent such trades
//    val priceIsNotTooLow: AbstractIndicator[Boolean] =
//      for {
//        price <- shortTarget
//        bound <- midChannelIndicator
//      } yield price.isGreaterThan(bound)
//
//    val priceIsNotTooHigh: AbstractIndicator[Boolean] =
//      for {
//        price <- longTarget
//        bound <- midChannelIndicator
//      } yield price.isLessThan(bound)

    val entryShortRule =
      channel.map(_.isDefined).asRule &
        channelIsWideEnough.asRule &
        new CrossedDownIndicatorRule(closePrice, upperBoundIndicator) &
        new UnderIndicatorRule(macd, macdEma)


    //        new LessThanIndicator(closePrice, upperBoundIndicator, bars = 1).asRule &
//        new GreaterThanIndicator(closePrice, upperBoundIndicator, bars = 1, offset = 1).asRule &
//        new OverIndicatorRule(closePrice, sma)

//        channelIsWideEnough.asRule &
//        new OverIndicatorRule(rsi, 55) &
//        volumeRule //&
//

    val entryLongRule =
      channel.map(_.isDefined).asRule &
        channelIsWideEnough.asRule &
        new CrossedUpIndicatorRule(closePrice, lowerBoundIndicator) &
        new OverIndicatorRule(macd, macdEma)

//        new GreaterThanIndicator(closePrice, lowerBoundIndicator, bars = 2).asRule &
//        new LessThanIndicator(closePrice, lowerBoundIndicator, bars = 1, offset = 2).asRule

//      channel.exists[Channel](c => c.k.lower > 0 && c.k.lower > -maxK).asRule &
//        new GreaterThanIndicator(closePrice, lowerBoundIndicator, bars = 1).asRule &
//        new LessThanIndicator(closePrice, lowerBoundIndicator, bars = 2, offset = 1).asRule &
//        new GreaterThanIndicator(closePrice, lowerBoundIndicator, bars = 2, offset = 2).asRule
//        new CrossedUpIndicatorRule(closePrice, lowerBoundIndicator) //&
//        channelIsWideEnough.asRule &
//        new UnderIndicatorRule(rsi, 45) &
//        volumeRule //&

    val exitRule = new AbstractRule {
      override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
        Option(tradingRecord.getCurrentPosition) match {
          case Some(position) =>
            val entryIndex = position.getEntry.getIndex
            val h = halfChannel.getValue(entryIndex)
            val price = closePrice.getValue(index)
            val entryPrice = closePrice.getValue(entryIndex)
            price.isGreaterThanOrEqual(entryPrice.plus(h)) ||
            price.isLessThanOrEqual(entryPrice.minus(h))
          case None => false
        }
    } //.or(channelIsDefinedRule.negation) TODO maybe need to fix TODO in channel ind and uncomment this

    val buyingStrategy = new BaseStrategy(entryLongRule, exitRule)
    val sellingStrategy = new BaseStrategy(entryShortRule, exitRule)

    def visualPriceIndicators() = {
      val visualExtremum: AbstractIndicator[Option[Extremum]] =
        extremum.shifted(extremumWindowSize / 2, None)
      val visualMinExtr = visualExtremum.map(_.collect { case extr: Extremum.Min => extr })
      val visualMaxExtr = visualExtremum.map(_.collect { case extr: Extremum.Max => extr })

      val visualChannel: AbstractIndicator[Option[Channel]] =
        new VisualChannelIndicator(channel)
      val visualLowerBoundIndicator =
        visualChannel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
      val visualUpperBoundIndicator =
        visualChannel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))

      val takeProfitIndicator = (closePrice: AbstractIndicator[Num]).zipWithIndex
        .map { case (index, price) =>
          if (entryShortRule.isSatisfied(index))
            (closePrice \-\ halfChannel).getValue(index)
          else if (entryLongRule.isSatisfied(index))
            (closePrice \+\ halfChannel).getValue(index)
          else NaN.NaN
        }

      val stopLossIndicator = (closePrice: AbstractIndicator[Num]).zipWithIndex
        .map { case (index, price) =>
          if (entryShortRule.isSatisfied(index))
            price.plus(halfChannel.getValue(index))
          else if (entryLongRule.isSatisfied(index))
            price.minus(halfChannel.getValue(index))
          else NaN.NaN
        }

      Map(
        "close price" -> IndicatorInfo(closePrice),
        "extrMin" -> IndicatorInfo(
          visualMinExtr.map {
            case Some(extr) => extr.value
            case None       => NaN.NaN
          },
          Points
        ),
        "extrMax" -> IndicatorInfo(
          visualMaxExtr.map {
            case Some(extr) => extr.value
            case None       => NaN.NaN
          },
          Representation.Points
        ),
        "lowerBound" -> IndicatorInfo(visualLowerBoundIndicator),
        "upperBound" -> IndicatorInfo(visualUpperBoundIndicator),
        "takeProfit" -> IndicatorInfo(takeProfitIndicator, Representation.Points),
        "stopLoss" -> IndicatorInfo(stopLossIndicator, Representation.Points),
        "mid" -> IndicatorInfo(midChannelIndicator),
        "sma" -> IndicatorInfo(sma)
      )
    }
    FullStrategy(
      longStrategy = sellingStrategy,
      shortStrategy = buyingStrategy,
      getPriceIndicators = visualPriceIndicators,
      oscillators = Map(
//        "hasData" -> IndicatorInfo(hasData.map(if (_) num(50) else series.num(0))),
//        "width" -> IndicatorInfo(relativeWidthIndicator),

//        "volume" -> IndicatorInfo(volumeRsi),
//        "rsi" -> IndicatorInfo(rsi)
        "macd" -> IndicatorInfo(macd),
        "macdEma" -> IndicatorInfo(macdEma)
      )
    )
  }

}
