package com.github.ppotseluev.algorate.ta4j.strategy

import com.github.ppotseluev.algorate.ta4j.indicator.{HasDataIndicator, LocalExtremumIndicator}
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
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
import org.ta4j.core.{BarSeries, BaseStrategy, Strategy}

import scala.jdk.CollectionConverters._
import com.github.ppotseluev.algorate.ta4j._
import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.ta4j.indicator._
import com.github.ppotseluev.algorate.ta4j.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.ta4j.strategy.FullStrategy.{IndicatorInfo, Representation}
import com.github.ppotseluev.algorate.util.Approximator
import org.ta4j.core.indicators.keltner.{
  KeltnerChannelLowerIndicator,
  KeltnerChannelMiddleIndicator,
  KeltnerChannelUpperIndicator
}
import org.ta4j.core.num.{NaN, Num}

import java.time.{LocalTime, ZonedDateTime}

object Strategies {
  val doNothing: Strategy = new BaseStrategy(
    BooleanRule.FALSE,
    BooleanRule.FALSE
  )

  val emaCrossAndRsiStrategy: BarSeries => FullStrategy = series => {
    //todo consider time zone here...
    val tradeTimeRange = new TimeRange(
      LocalTime.of(7, 0),
      LocalTime.of(16, 0)
    )
    val time: AbstractIndicator[ZonedDateTime] = new DateTimeIndicator(series)
    val hasData: AbstractIndicator[Boolean] = new HasDataIndicator(40, series)
    val closePrice = new ClosePriceIndicator(series)
    val rsi = new RSIIndicator(closePrice, 14)
    val emaFast = new EMAIndicator(closePrice, 15)
    val emaSlow = new EMAIndicator(closePrice, 40)
    val exitRule =
      new StopLossRule(closePrice, 0.5).or(
        new StopGainRule(closePrice, 0.5)
      )
    val coreLongRule = new ChainRule(
      new BooleanRule(true),
      new ChainLink(new CrossedUpIndicatorRule(emaFast, emaSlow), 0),
      new ChainLink(new CrossedUpIndicatorRule(rsi, 40), 5)
    )
    val timeRule =
      new TimeRangeRule(Seq(tradeTimeRange).asJava, time.asInstanceOf[DateTimeIndicator])
    val entryLongRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
        timeRule &
        coreLongRule
    val buyingStrategy = new BaseStrategy(entryLongRule, exitRule, 40)
    val entryShortRule =
      new ChainRule(
        new BooleanRule(true),
        new ChainLink(new CrossedDownIndicatorRule(emaFast, emaSlow), 0),
        new ChainLink(new CrossedDownIndicatorRule(rsi, 60), 5)
      )
    val sellingStrategy = new BaseStrategy(entryShortRule, exitRule)
    FullStrategy(
      longStrategy = buyingStrategy,
      shortStrategy = sellingStrategy,
      priceIndicators = Map(
        "close price" -> IndicatorInfo(closePrice),
        "ema fast" -> IndicatorInfo(emaFast),
        "ema slow" -> IndicatorInfo(emaSlow)
      ),
      oscillators = Map(
//        "rsi" -> rsi,
//        "stable" -> hasData.map {
//          if (_) series.numOf(50) else series.numOf(0)
//        },
//        "time" -> time
//          .map(_.toLocalTime)
//          .map(t => !t.isBefore(tradeTimeRange.getFrom) && !t.isAfter(tradeTimeRange.getTo))
//          .map {
//            if (_) series.numOf(25) else series.numOf(0)
//          }
      )
    )
  }

  val test: BarSeries => FullStrategy = series => {
    //todo consider time zone here...
    val tradeTimeRange = new TimeRange(
      LocalTime.of(7, 0),
      LocalTime.of(16, 0)
    )
    val time: AbstractIndicator[ZonedDateTime] = new DateTimeIndicator(series)
    val hasData: AbstractIndicator[Boolean] = new HasDataIndicator(40, series)
    val closePrice = new ClosePriceIndicator(series)

//    val kM = new KeltnerChannelMiddleIndicator(closePrice, 20)
//    val kL = new KeltnerChannelLowerIndicator(kM, 2, 20)
//    val kU = new KeltnerChannelUpperIndicator(kM, 2, 20)

//    val dpo = new DPOIndicator(closePrice, 20)
//    val fisher = new FisherIndicator(series)
    val parabolic = new ParabolicSarIndicator(series)

    val exitRule =
      new StopLossRule(closePrice, 0.5).or(
        new StopGainRule(closePrice, 0.5)
      )
    val coreLongRule = new BooleanRule(false)
    val timeRule =
      new TimeRangeRule(Seq(tradeTimeRange).asJava, time.asInstanceOf[DateTimeIndicator])
    val entryLongRule =
      new BooleanIndicatorRule(hasData.map(boolean2Boolean)) &
        timeRule &
        coreLongRule
    val buyingStrategy = new BaseStrategy(entryLongRule, exitRule)
    val extremumWindowSize = 60
    val extremum: AbstractIndicator[Option[Extremum]] =
      LocalExtremumIndicator(closePrice, extremumWindowSize)
    val minExtr = extremum.map(_.collect { case extr: Extremum.Min => extr })
    val maxExtr = extremum.map(_.collect { case extr: Extremum.Max => extr })
    val channel: AbstractIndicator[Option[Channel]] = new ChannelIndicator(
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
      maxError = 5
    )
//    val visualChannel: AbstractIndicator[Option[Channel]] = new VisualChannelIndicator(channel)
    val parChannel: AbstractIndicator[Option[Channel]] =
      new ParallelChannel(channel, maxDelta = 0.3)
    val lowerBoundIndicator = parChannel.map(_.map(_.section.lowerBound).getOrElse(NaN.NaN))
    val upperBoundIndicator = parChannel.map(_.map(_.section.upperBound).getOrElse(NaN.NaN))
    FullStrategy(
      longStrategy = buyingStrategy,
      shortStrategy = doNothing,
      priceIndicators = Map(
        "close price" -> IndicatorInfo(closePrice),
        "extrMin" -> IndicatorInfo(
          minExtr.map {
            case Some(extr) => extr.value
            case None       => NaN.NaN
          },
          Representation.Points
        ),
        "extrMax" -> IndicatorInfo(
          maxExtr.map {
            case Some(extr) => extr.value
            case None       => NaN.NaN
          },
          Representation.Points
        ),
        "lowerBound" -> IndicatorInfo(lowerBoundIndicator),
        "upperBound" -> IndicatorInfo(upperBoundIndicator)
      ),
      oscillators = Map.empty
    )
  }

}
