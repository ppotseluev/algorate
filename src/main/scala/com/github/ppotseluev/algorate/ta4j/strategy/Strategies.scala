package com.github.ppotseluev.algorate.ta4j.strategy

import com.github.ppotseluev.algorate.ta4j.indicator.HasDataIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.{AbstractIndicator, DateTimeIndicator, EMAIndicator, RSIIndicator}
import org.ta4j.core.rules.TimeRangeRule.TimeRange
import org.ta4j.core.rules.helper.ChainLink
import org.ta4j.core.rules._
import cats.syntax.functor._
import org.ta4j.core.{BarSeries, BaseStrategy, Strategy}
import scala.jdk.CollectionConverters._
import com.github.ppotseluev.algorate.ta4j._

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
        "close price" -> closePrice,
        "ema fast" -> emaFast,
        "ema slow" -> emaSlow
      ),
      oscillators = Map(
        "rsi" -> rsi,
        "stable" -> hasData.map {
          if (_) series.numOf(50) else series.numOf(0)
        },
        "time" -> time
          .map(_.toLocalTime)
          .map(t => !t.isBefore(tradeTimeRange.getFrom) && !t.isAfter(tradeTimeRange.getTo))
          .map {
            if (_) series.numOf(25) else series.numOf(0)
          }
      )
    )
  }
}
