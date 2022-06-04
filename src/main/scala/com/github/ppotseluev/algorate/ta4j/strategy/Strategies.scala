package com.github.ppotseluev.algorate.ta4j.strategy

import com.github.ppotseluev.algorate.ta4j.indicator.{HasDataIndicator, LastLocalExtremumIndicator}
import org.ta4j.core.indicators.helpers.{
  ClosePriceIndicator,
  ConstantIndicator,
  DifferenceIndicator,
  FixedDecimalIndicator,
  SumIndicator,
  VolumeIndicator
}
import org.ta4j.core.indicators.{
  AbstractIndicator,
  DPOIndicator,
  DateTimeIndicator,
  EMAIndicator,
  FisherIndicator,
  KSTIndicator,
  ParabolicSarIndicator,
  RSIIndicator
}
import org.ta4j.core.rules.TimeRangeRule.TimeRange
import org.ta4j.core.rules.helper.ChainLink
import org.ta4j.core.rules._
import cats.syntax.functor._
import cats.syntax.flatMap._
import org.ta4j.core.{BarSeries, BaseStrategy, Strategy}

import scala.jdk.CollectionConverters._
import com.github.ppotseluev.algorate.ta4j._
import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.ta4j.indicator._
import com.github.ppotseluev.algorate.ta4j.indicator.LastLocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy.{IndicatorInfo, Representation}
import com.github.ppotseluev.algorate.util.Approximator
import com.github.ppotseluev.algorate.util.Approximator.Approximation
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.indicators.keltner.{
  KeltnerChannelLowerIndicator,
  KeltnerChannelMiddleIndicator,
  KeltnerChannelUpperIndicator
}
import org.ta4j.core.indicators.volume.ChaikinOscillatorIndicator
import org.ta4j.core.num.{NaN, Num}

import java.time.{LocalTime, ZonedDateTime}
import scala.language.implicitConversions

object Strategies {
  val doNothing: Strategy = new BaseStrategy(
    BooleanRule.FALSE,
    BooleanRule.FALSE
  )

  val test: BarSeries => FullStrategy = series => {
    def num(number: Number): Num =
      series.numOf(number)

    //todo consider time zone here...
    val tradeTimeRange = new TimeRange(
      LocalTime.of(7, 0),
      LocalTime.of(16, 0)
    )
    val time: AbstractIndicator[ZonedDateTime] = new DateTimeIndicator(series)
    val closePrice = new ClosePriceIndicator(series)
    val volumeIndicator: AbstractIndicator[Num] = new VolumeIndicator(series)
    val extremumWindowSize = 30
    val hasData: AbstractIndicator[Boolean] = new HasDataIndicator(extremumWindowSize, series)
    val _extremum: AbstractIndicator[Option[Extremum]] =
      LastLocalExtremumIndicator(closePrice, extremumWindowSize)
    val extremum: AbstractIndicator[Option[Extremum]] = _extremum
//      new FilteredExtremumIndicator(
//        extremumIndicator = _extremum,
//        minIndexDelta = 10,
//        minPercent = 0.01
//      )
    val visualExtremum: AbstractIndicator[Option[Extremum]] =
      extremum.shifted(extremumWindowSize / 2, None)
    val visualMinExtr = visualExtremum.map(_.collect { case extr: Extremum.Min => extr })
    val visualMaxExtr = visualExtremum.map(_.collect { case extr: Extremum.Max => extr })
    val minRelativeWidth = num(0.002)//todo...
    val channel: AbstractIndicator[Option[Channel]] = ChannelIndicator(
      baseIndicator = closePrice,
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
//      maxError = 3
      maxError = 0.0009 //0.0007
    ).filter(ChannelUtils.isParallel(maxDelta = 0.6)) //todo?
//      .filter(ChannelUtils.isWide(minPercent = 0.05))
//todo check we are close to lower bound
    val rsi = new RSIIndicator(closePrice, 14)

    //TODO was (6, 0.5). (2, 0.2) - better?

    val lowerBoundIndicator = channel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
    val upperBoundIndicator = channel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))

    val visualChannel: AbstractIndicator[Option[Channel]] =
      new VisualChannelIndicator(channel)
    val visualLowerBoundIndicator =
      visualChannel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
    val visualUpperBoundIndicator =
      visualChannel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))

    val channelDiffIndicator: AbstractIndicator[Num] =
      new DifferenceIndicator(upperBoundIndicator, lowerBoundIndicator)
    val halfChannel = channelDiffIndicator.map(_.dividedBy(num(2)))

    val channelIsDefinedRule = new BooleanIndicatorRule(channel.map(_.isDefined))
    val exitLongRule =
      //todo consider channel width
      //todo exit on Nan channel ?
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
        new BooleanIndicatorRule(volumeIndicator.map(_.isGreaterThan(num(500)))) & //TODO research... maybe use some derived ind?
        new BooleanIndicatorRule(relativeWidthIndicator.map(_.isGreaterThan(minRelativeWidth)))
    val coreShortRule = new CrossedDownIndicatorRule(closePrice, upperBoundIndicator)
    val timeRule =
      new TimeRangeRule(Seq(tradeTimeRange).asJava, time.asInstanceOf[DateTimeIndicator])
    val entryLongRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
        timeRule &
        coreLongRule
    val entryShortRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
        timeRule &
        coreShortRule
    val buyingStrategy = new BaseStrategy(entryLongRule, exitLongRule)
//    val sellingStrategy = new BaseStrategy(entryShortRule, exitRule)
//    val ema = new EMAIndicator(closePrice, 3 * extremumWindowSize)
    FullStrategy(
      longStrategy = buyingStrategy,
      shortStrategy = doNothing,
      priceIndicators = Map(
//        "ema" -> IndicatorInfo(ema),
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
      ),
      oscillators = Map(
//        "rsi" -> IndicatorInfo(rsi),
//        "hasData" -> IndicatorInfo(hasData.map(if (_) num(50) else series.num(0))),
//        "width" -> IndicatorInfo(relativeWidthIndicator),
        "volume" -> IndicatorInfo(volumeIndicator)
      )
    )
  }

}
