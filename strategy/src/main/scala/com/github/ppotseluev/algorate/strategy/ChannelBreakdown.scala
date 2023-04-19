package com.github.ppotseluev.algorate.strategy

import cats.implicits._
import com.github.ppotseluev.algorate.math.Approximator
import com.github.ppotseluev.algorate.strategy.FullStrategy.{IndicatorInfo, Representation}
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation.Points
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.strategy.indicator.{
  ChannelIndicator,
  ChannelUtils,
  HasDataIndicator,
  IndicatorSyntax,
  LocalExtremumIndicator,
  VisualChannelIndicator,
  _
}
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.{BarSeries, BaseStrategy, TradingRecord}
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.helpers.{
  ClosePriceIndicator,
  DifferenceIndicator,
  SumIndicator,
  VolumeIndicator
}
import org.ta4j.core.num.{NaN, Num}
import org.ta4j.core.rules._

private[strategy] object ChannelBreakdown {

  def apply(): BarSeries => FullStrategy = implicit series => {
    def num(number: Number): Num =
      series.numOf(number)
    val closePrice = new ClosePriceIndicator(series)
    val extremumWindowSize = 30
    val extremum: AbstractIndicator[Option[Extremum]] =
      LocalExtremumIndicator(closePrice, extremumWindowSize)
    val channel: AbstractIndicator[Option[Channel]] = ChannelIndicator(
      baseIndicator = closePrice,
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
      maxError = 0.0009
    )
      .filter(ChannelUtils.isParallel(maxDelta = 0.6))
      .filter(ChannelUtils.isWide(0.1))

    val feeFraction = 0.0005
    val leastFeeFactor = 2

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

//    val stopLossRule: AbstractRule = new AbstractRule {
//      private val percentageIndicator: AbstractIndicator[Num] = for {
//        half <- halfChannel
//        p <- closePrice: AbstractIndicator[Num]
//      } yield half.dividedBy(p).multipliedBy(num(100)).dividedBy(num(2))
//
//      override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
//        Option(tradingRecord.getCurrentPosition) match {
//          case Some(position) =>
//            val entryIndex = position.getEntry.getIndex
//            val percentage = percentageIndicator.getValue(entryIndex)
//            val res =
//              new TrailingStopLossRule(closePrice, percentage).isSatisfied(index, tradingRecord)
//            res
//          case None => false
//        }
//    }

    val stopLossRule = new AbstractRule {
      override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
        Option(tradingRecord.getCurrentPosition) match {
          case Some(position) =>
            val entryIndex = position.getEntry.getIndex
            val target = closePrice
              .getValue(entryIndex)
              .minus(halfChannel.getValue(entryIndex))
            closePrice.getValue(index).isLessThanOrEqual(target)
          case None => false
        }
    }

    val stopGainRule = new AbstractRule {
      override def isSatisfied(index: Int, tradingRecord: TradingRecord): Boolean =
        Option(tradingRecord.getCurrentPosition) match {
          case Some(position) =>
            val entryIndex = position.getEntry.getIndex
            val target = closePrice
              .getValue(entryIndex)
              .plus(channelDiffIndicator.getValue(entryIndex))
            closePrice.getValue(index).isGreaterThanOrEqual(target)
          case None => false
        }
    }

    val exitLongRule = stopLossRule.or(stopGainRule)

    val coreLongRule =
      channelIsDefinedRule &
        new BooleanIndicatorRule(
          channel.map(
            _.exists { c =>
              val k = c.lowerBoundApproximation.func
                .asInstanceOf[PolynomialFunction]
                .getCoefficients
                .last
//              println(k)
              k < -2e-5
            }
          )
        ) &
        new BooleanIndicatorRule(
          new GreaterThanIndicator(closePrice, upperBoundIndicator, bars = 2)
            .map(boolean2Boolean)
        )

    val buyingStrategy = new BaseStrategy(coreLongRule, exitLongRule)

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
          if (coreLongRule.isSatisfied(index))
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
      shortStrategy = Strategies.doNothing,
      getPriceIndicators = visualPriceIndicators,
      oscillators = Map.empty
    )
  }

}
