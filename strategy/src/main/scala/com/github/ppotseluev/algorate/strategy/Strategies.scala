package com.github.ppotseluev.algorate.strategy

import cats.implicits._
import com.github.ppotseluev.algorate.math.Approximator
import com.github.ppotseluev.algorate.strategy.FullStrategy.IndicatorInfo
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation.Points
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator
import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.strategy.indicator.ChannelUtils
import com.github.ppotseluev.algorate.strategy.indicator.IndicatorSyntax
import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator
import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.strategy.indicator.VisualChannelIndicator
import com.github.ppotseluev.algorate.strategy.indicator._
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseStrategy
import org.ta4j.core.Strategy
import org.ta4j.core.TradingRecord
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.MACDIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.{
  ClosePriceIndicator,
  DifferenceIndicator,
  SumIndicator,
  TradeCountIndicator,
  VolumeIndicator
}
import org.ta4j.core.num.NaN
import org.ta4j.core.num.Num
import org.ta4j.core.rules._

import scala.concurrent.duration._

object Strategies {
  val doNothing: Strategy = new BaseStrategy(
    BooleanRule.FALSE,
    BooleanRule.FALSE
  )

  val intraChannel = IntraChannel()
  val channelBreakdown = ChannelBreakdown()

  def random(
      enterChance: Double = 0.01,
      exitChance: Double = 0.05
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

  case class Params(
      extremumWindowSize: Int = 15,
      maxError: Double = 0.002,
      maxParallelDelta: Double = 0.6,
      minPotentialChange: Double = 0.004,
      shortMacdPeriod: Int = 12
  )

  val default = createDefault(Params())

  def createDefault(params: Params): BarSeries => FullStrategy = implicit series => {
    import params._

    def num(number: Number): Num =
      series.numOf(number)

    val closePrice = new ClosePriceIndicator(series)
    val extremum: AbstractIndicator[Option[Extremum]] =
      LocalExtremumIndicator(closePrice, extremumWindowSize)
    val channel: AbstractIndicator[Option[Channel]] = ChannelIndicator(
      baseIndicator = closePrice,
      extremumIndicator = extremum,
      approximator = Approximator.Linear,
      numOfPoints = 3,
      maxError = maxError
    ).filter(ChannelUtils.isParallel(maxParallelDelta)) //todo?

    val tradesCountIndicator =
      (new TradeCountIndicator(series): AbstractIndicator[java.lang.Long]).map(num)
    val tradesFastSma: AbstractIndicator[Num] = // tradesCountIndicator
      new SMAIndicator(tradesCountIndicator, 4)
    val tradesSlowSma: AbstractIndicator[Num] = new SMAIndicator(tradesCountIndicator, 3000)
    val tradesUpper = tradesSlowSma.map(_.multipliedBy(num(1.5)))
    val tradesLower = tradesSlowSma.map(_.multipliedBy(num(0.5)))

    val normalTrades = for {
      tradesCount <- tradesFastSma
      upper <- tradesUpper
      lower <- tradesLower
    } yield {
      tradesCount.isGreaterThanOrEqual(lower) &&
      tradesCount.isLessThanOrEqual(upper)
    }

    // Define MACD parameters
    val shortPeriod = shortMacdPeriod
    val longPeriod = 2 * shortPeriod
    val signalPeriod = longPeriod / 3
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
      } yield h.dividedBy(p).isGreaterThan(num(minPotentialChange))

    val entryLongRule =
      channel.map(_.isDefined).asRule &
        channelIsWideEnough.asRule &
        new CrossedDownIndicatorRule(closePrice, upperBoundIndicator) &
        new UnderIndicatorRule(macd, macdEma) &
        normalTrades.asRule //&
//        channel.exists[Channel](c => c.k.upper > 0).asRule

    val entryShortRule =
      channel.map(_.isDefined).asRule &
        channelIsWideEnough.asRule &
        new CrossedUpIndicatorRule(closePrice, lowerBoundIndicator) &
        new OverIndicatorRule(macd, macdEma) &
        normalTrades.asRule //&
//        channel.exists[Channel](c => c.k.lower < 0).asRule

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
    }

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
        "mid" -> IndicatorInfo(midChannelIndicator)
      )
    }
    FullStrategy(
      longStrategy = buyingStrategy,
      shortStrategy = sellingStrategy,
      getPriceIndicators = visualPriceIndicators,
      oscillators = Map(
        "macd" -> IndicatorInfo(macd),
        "macdEma" -> IndicatorInfo(macdEma)
      )
    )
  }

}
