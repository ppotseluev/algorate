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
//    def hasAlternatingExtremums(channel: Channel): Boolean = {
//      val mins = channel.allExtremums.lower
//      val maxs = channel.allExtremums.upper
//      (mins ++ maxs)
//        .sortBy(_.index)
//        .foldLeft((true, none[Extremum])) { case ((accFlag, last), extr) =>
//          def ok = (last, extr) match {
//            case (Some(_: Extremum.Max), _: Extremum.Min) => true
//            case (Some(_: Extremum.Min), _: Extremum.Max) => true
//            case (None, _)                                => true
//            case _                                        => false
//          }
//          (accFlag && ok) -> extr.some
//        }
//        ._1
//    }

    def num(number: Number): Num =
      series.numOf(number)

//    it's not very useful cuz different assets may have different trading periods.
//    So `hasFutureData` is used for now instead.
//    val tradeTimeRange = new TimeRange(
//      LocalTime.of(7, 0),
//      LocalTime.of(16, 0)
//    )
//    val time: AbstractIndicator[ZonedDateTime] = new DateTimeIndicator(series)
    val closePrice = new ClosePriceIndicator(series)
    val volumeIndicator: AbstractIndicator[Num] = new VolumeIndicator(series)
    val extremumWindowSize = 30
    val hasData: AbstractIndicator[Boolean] = new HasDataIndicator(extremumWindowSize, series)
    val hasFutureData =
      hasData.shifted( //uses future data, won't be available in real life but it's solvable
        extremumWindowSize,
        defaultValue = false
      )
    val _extremum: AbstractIndicator[Option[Extremum]] =
      LocalExtremumIndicator(closePrice, extremumWindowSize)
    val extremum: AbstractIndicator[Option[Extremum]] = _extremum
//      new FilteredExtremumIndicator(
//        extremumIndicator = _extremum,
//        minIndexDelta = 10,
//        minPercent = 0.01
//      )
//    val minRelativeWidth = num(0.002) //todo...
    val channel: AbstractIndicator[Option[Channel]] = ChannelIndicator(
      baseIndicator = closePrice,
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
      maxError = 0.0009 //0.0007 0.003🤔
    ).filter(ChannelUtils.isParallel(maxDelta = 0.6)) //todo?

    //TODO was (6, 0.5). (2, 0.2) - better?

    val rsi = new RSIIndicator(closePrice, 5)
    val volumeRsi = new RSIIndicator(volumeIndicator, 5)

    val highVolume: AbstractIndicator[Num] =
      new PercentileIndicator(volumeIndicator, percentile = 75, barCount = extremumWindowSize)
//    val lowVolume: AbstractIndicator[Num] =
//      new PercentileIndicator(volumeIndicator, percentile = 20, barCount = 10)

    val volumeRule =
      new UnderIndicatorRule(volumeRsi, 60) &
        new OverIndicatorRule(volumeRsi, 40)

    //      new UnderIndicatorRule(volumeIndicator, highVolume)
    //      new OverIndicatorRule(volumeIndicator, lowVolume)

    val feeFraction = 0.0005
    val leastFeeFactor = 2
    val maxK = Int.MaxValue

    val minVolume = num(Int.MinValue)
    val maxVolume = num(Int.MaxValue)

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

//    val exitLongRule =
//      todo consider channel width
//      new StopLossRule(closePrice, 1)
//        .or(new StopGainRule(closePrice, 1))
//        .or(new CrossedUpIndicatorRule(closePrice, midChannelIndicator))
//        .or(new CrossedDownIndicatorRule(closePrice, lowerBoundIndicator \-\ halfChannel))
//        .or(channelIsDefinedRule.negation)

    def exitRule(long: Boolean) = new AbstractRule {
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

    val priceIsNotTooHigh: AbstractIndicator[java.lang.Boolean] =
      for {
        price <- leastLongTarget
        bound <- midChannelIndicator
      } yield price.isLessThan(bound)

    val coreShortRule =
      channelIsDefinedRule &
        new BooleanIndicatorRule(
          channel.map(
            _.exists { c =>
              val k = c.lowerBoundApproximation.func
                .asInstanceOf[PolynomialFunction]
                .getCoefficients
                .last
              k > 0 && k < maxK
            }
          )
        ) &
        volumeRule &
        new OverIndicatorRule(rsi, 55) &
        new CrossedDownIndicatorRule(
          closePrice,
          lowerBoundIndicator
        ) &
        new UnderIndicatorRule(lowerBoundIndicator \-\ halfChannel, leastShortTarget)

    ///  \-\ halfChannel.map(_.dividedBy(num(4))) ) &
//    new BooleanIndicatorRule(priceIsNotTooHigh) &

//        new BooleanIndicatorRule(volumeIndicator.map(_.isLessThan(maxVolume))) &
//        new BooleanIndicatorRule(volumeIndicator.map(_.isGreaterThan(minVolume)))

    val coreLongRule = {
      val priceIsNotTooLow: AbstractIndicator[java.lang.Boolean] =
        for {
          price <- leastShortTarget
          bound <- midChannelIndicator
        } yield price.isGreaterThan(bound)
      channelIsDefinedRule &
        new BooleanIndicatorRule(
          channel.map(
            _.exists { c =>
              val k = c.upperBoundApproximation.func
                .asInstanceOf[PolynomialFunction]
                .getCoefficients
                .last
              k < 0 && k > -maxK
            }
          )
        ) &
        volumeRule &
        new UnderIndicatorRule(rsi, 45) &
        new CrossedUpIndicatorRule(
          closePrice,
          upperBoundIndicator
        ) &
        new OverIndicatorRule(upperBoundIndicator \+\ halfChannel, leastLongTarget)

      // \+\ halfChannel.map(_.dividedBy(num(4)))) &
//      new BooleanIndicatorRule(priceIsNotTooLow) &
//        new BooleanIndicatorRule(volumeIndicator.map(_.isLessThan(maxVolume))) &
//        new BooleanIndicatorRule(volumeIndicator.map(_.isGreaterThan(minVolume)))
    }
//    val timeRule =
//      new TimeRangeRule(Seq(tradeTimeRange).asJava, time.asInstanceOf[DateTimeIndicator])
    val entryLongRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
        new BooleanIndicatorRule(hasFutureData.map(boolean2Boolean)) &
//        timeRule &
        coreLongRule
    val entryShortRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
        new BooleanIndicatorRule(hasFutureData.map(boolean2Boolean)) &
//        timeRule &
        coreShortRule
//    val exitShortRule =
//      new StopLossRule(closePrice, 1)
//        .or(new StopGainRule(closePrice, 1))
//        .or(new CrossedDownIndicatorRule(closePrice, midChannelIndicator))
//        .or(new CrossedUpIndicatorRule(closePrice, upperBoundIndicator \+\ halfChannel))
//        .or(channelIsDefinedRule.negation)
    val buyingStrategy = new BaseStrategy(entryLongRule, exitRule(long = true))
    val sellingStrategy = new BaseStrategy(entryShortRule, exitRule(long = false))

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
