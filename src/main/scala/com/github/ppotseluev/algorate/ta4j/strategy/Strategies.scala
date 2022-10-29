package com.github.ppotseluev.algorate.ta4j.strategy

import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.ta4j._
import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.ta4j.indicator.HasDataIndicator
import com.github.ppotseluev.algorate.ta4j.indicator.LocalExtremumIndicator
import com.github.ppotseluev.algorate.ta4j.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.ta4j.indicator._
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy.IndicatorInfo
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy.Representation
import com.github.ppotseluev.algorate.util.Approximator
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseStrategy
import org.ta4j.core.Strategy
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.DifferenceIndicator
import org.ta4j.core.indicators.helpers.SumIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import org.ta4j.core.rules._

object Strategies {
  val doNothing: Strategy = new BaseStrategy(
    BooleanRule.FALSE,
    BooleanRule.FALSE
  )

  val intraChannel: BarSeries => FullStrategy = series => {
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
    val minRelativeWidth = num(0.002) //todo...
    val channel: AbstractIndicator[Option[Channel]] = ChannelIndicator(
      baseIndicator = closePrice,
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
      maxError = 0.0009 //0.0007
    ).filter(ChannelUtils.isParallel(maxDelta = 0.6)) //todo?
//      .filter(ChannelUtils.isWide(minPercent = 0.05))

    //TODO was (6, 0.5). (2, 0.2) - better?

    val lowerBoundIndicator = channel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
    val upperBoundIndicator = channel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))

    val channelDiffIndicator: AbstractIndicator[Num] =
      new DifferenceIndicator(upperBoundIndicator, lowerBoundIndicator)
    val halfChannel = channelDiffIndicator.map(_.dividedBy(num(2)))

    val channelIsDefinedRule = new BooleanIndicatorRule(channel.map(_.isDefined))
    val exitLongRule =
      //todo consider channel width
      new StopLossRule(closePrice, 1)
        .or(
          new StopGainRule(closePrice, 1)
        )
        .or(
          new CrossedUpIndicatorRule(
            closePrice,
            new SumIndicator(lowerBoundIndicator, halfChannel)
          )
        )
        .or(
          new CrossedDownIndicatorRule(
            closePrice,
            new DifferenceIndicator(lowerBoundIndicator, halfChannel)
          )
        )
        .or(
          channelIsDefinedRule.negation
        )

    val relativeWidthIndicator: AbstractIndicator[Num] =
      for {
        l <- lowerBoundIndicator
        u <- upperBoundIndicator
      } yield u.minus(l).dividedBy(u)

    val priceIsNotTooHigh: AbstractIndicator[java.lang.Boolean] =
      for {
        price <- closePrice: AbstractIndicator[Num]
        bound <-
          lowerBoundIndicator \+\ halfChannel //channelDiffIndicator.map(_.dividedBy(num(10)))
      } yield price.isLessThan(bound)

    val coreLongRule =
      channelIsDefinedRule &
        new BooleanIndicatorRule(
          channel.map(
            _.exists(
              _.lowerBoundApproximation.func
                .asInstanceOf[PolynomialFunction]
                .getCoefficients
                .last < 0 //todo 🤔🤔🤔
            )
          )
        ) &
        new CrossedUpIndicatorRule(closePrice, lowerBoundIndicator) &
        new BooleanIndicatorRule(priceIsNotTooHigh) &
        new BooleanIndicatorRule(
          volumeIndicator.map(_.isGreaterThan(num(500)))
        ) & //TODO research... maybe use some derived ind?
        new BooleanIndicatorRule(relativeWidthIndicator.map(_.isGreaterThan(minRelativeWidth)))
    val coreShortRule = {
      val priceIsNotTooLow: AbstractIndicator[java.lang.Boolean] =
        for {
          price <- closePrice: AbstractIndicator[Num]
          bound <-
            upperBoundIndicator \-\ halfChannel //channelDiffIndicator.map(_.dividedBy(num(10)))
        } yield price.isGreaterThan(bound)
      channelIsDefinedRule &
        new BooleanIndicatorRule(
          channel.map(
            _.exists(
              _.upperBoundApproximation.func
                .asInstanceOf[PolynomialFunction]
                .getCoefficients
                .last > 0
            )
          )
        ) &
        new CrossedDownIndicatorRule(closePrice, upperBoundIndicator) &
        new BooleanIndicatorRule(priceIsNotTooLow) &
        new BooleanIndicatorRule(
          volumeIndicator.map(_.isGreaterThan(num(500)))
        ) &
        new BooleanIndicatorRule(relativeWidthIndicator.map(_.isGreaterThan(minRelativeWidth)))
    }
//    val timeRule =
//      new TimeRangeRule(Seq(tradeTimeRange).asJava, time.asInstanceOf[DateTimeIndicator])
    val entryLongRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
//        new BooleanIndicatorRule(hasFutureData.map(boolean2Boolean)) &
//        timeRule &
        coreLongRule
    val entryShortRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
//        new BooleanIndicatorRule(hasFutureData.map(boolean2Boolean)) &
//        timeRule &
        coreShortRule
    val exitShortRule =
      new StopLossRule(closePrice, 1)
        .or(
          new StopGainRule(closePrice, 1)
        )
        .or(
          new CrossedDownIndicatorRule(
            closePrice,
            new DifferenceIndicator(upperBoundIndicator, halfChannel)
          )
        )
        .or(
          new CrossedUpIndicatorRule(
            closePrice,
            new SumIndicator(upperBoundIndicator, halfChannel)
          )
        )
        .or(
          channelIsDefinedRule.negation
        )
    val buyingStrategy = new BaseStrategy(entryLongRule, exitLongRule)
    val sellingStrategy = new BaseStrategy(entryShortRule, exitShortRule)

    val visualPriceIndicators = {
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
      Map(
        "close price" -> IndicatorInfo(closePrice),
        "extrMin" -> IndicatorInfo(
          visualMinExtr.map {
            case Some(extr) => extr.value
            case None       => NaN.NaN
          },
          Representation.Points
        ),
        "extrMax" -> IndicatorInfo(
          visualMaxExtr.map {
            case Some(extr) => extr.value
            case None       => NaN.NaN
          },
          Representation.Points
        ),
        "lowerBound" -> IndicatorInfo(visualLowerBoundIndicator),
        "upperBound" -> IndicatorInfo(visualUpperBoundIndicator)
      )
    }
    FullStrategy(
      longStrategy = buyingStrategy,
      shortStrategy = sellingStrategy,
      priceIndicators = visualPriceIndicators,
      oscillators = Map(
//        "hasData" -> IndicatorInfo(hasData.map(if (_) num(50) else series.num(0))),
//        "width" -> IndicatorInfo(relativeWidthIndicator),
        "volume" -> IndicatorInfo(volumeIndicator)
      )
    )
  }

}
