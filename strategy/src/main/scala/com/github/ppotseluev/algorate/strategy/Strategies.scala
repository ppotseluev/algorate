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
import com.github.ppotseluev.algorate.strategy.indicator._
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseStrategy
import org.ta4j.core.Strategy
import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.{AbstractIndicator, RSIIndicator, SMAIndicator}
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

    val feeFraction = 0.0005
    val leastFeeFactor = 2
    val maxK = Int.MaxValue

    val lowerBoundIndicator = channel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
    val upperBoundIndicator = channel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))

    val channelDiffIndicator: AbstractIndicator[Num] =
      new DifferenceIndicator(upperBoundIndicator, lowerBoundIndicator)
    val halfChannel = channelDiffIndicator.map(_.dividedBy(num(2)))

    val channelIsDefinedRule = new BooleanIndicatorRule(channel.map(_.isDefined))

    val leastLongTarget = (closePrice: AbstractIndicator[Num]).map { price =>
      price.multipliedBy(num(1 + leastFeeFactor * feeFraction))
    }

    val leastShortTarget = (closePrice: AbstractIndicator[Num]).map { price =>
      price.multipliedBy(num(1 - leastFeeFactor * feeFraction))
    }

    val midChannelIndicator: AbstractIndicator[Num] =
      new SumIndicator(lowerBoundIndicator, halfChannel)

    val priceIsNotTooLow: AbstractIndicator[java.lang.Boolean] =
      for {
        price <- leastShortTarget
        bound <- midChannelIndicator
      } yield price.isGreaterThan(bound)

    val priceIsNotTooHigh: AbstractIndicator[java.lang.Boolean] =
      for {
        price <- leastLongTarget
        bound <- midChannelIndicator
      } yield price.isLessThan(bound)

    val entryShortRule =
      channel.exists[Channel](c => c.k.upper > 0 && c.k.upper < maxK).asRule &
        new CrossedDownIndicatorRule(closePrice, upperBoundIndicator) &
        new BooleanIndicatorRule(priceIsNotTooLow)

    val entryLongRule =
      channel.exists[Channel](c => c.k.upper < 0 && c.k.upper > -maxK).asRule &
        new CrossedUpIndicatorRule(closePrice, lowerBoundIndicator) &
        new BooleanIndicatorRule(priceIsNotTooHigh)

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
    }.or(channelIsDefinedRule.negation)

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
      val leastTargetIndicator = (closePrice: AbstractIndicator[Num]).zipWithIndex
        .map { case (index, price) =>
          if (entryShortRule.isSatisfied(index))
            price.multipliedBy(num(1 - leastFeeFactor * feeFraction))
          else if (entryLongRule.isSatisfied(index))
            price.multipliedBy(num(1 + leastFeeFactor * feeFraction))
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
        "leastTarget" -> IndicatorInfo(leastTargetIndicator, Representation.Points),
        "mid" -> IndicatorInfo(midChannelIndicator)
      )
    }
    FullStrategy(
      longStrategy = buyingStrategy,
      shortStrategy = sellingStrategy,
      getPriceIndicators = visualPriceIndicators,
      oscillators = Map(
//        "hasData" -> IndicatorInfo(hasData.map(if (_) num(50) else series.num(0))),
//        "width" -> IndicatorInfo(relativeWidthIndicator),
        "volume" -> IndicatorInfo(volumeRsi)
//        "rsi" -> IndicatorInfo(rsi)
      )
    )
  }

}
