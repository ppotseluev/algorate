package com.github.ppotseluev.algorate.strategy.ta4j.indicator

import cats.data.NonEmptyList
import cats.syntax.functor._
import com.github.ppotseluev.algorate.math.Approximator
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.Bounds
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.CalculatedChannel
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.Channel
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.ChannelState
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.NeedNewChannel
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.Section
import com.github.ppotseluev.algorate.strategy.ta4j.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.math.Approximator.Approximation
import com.github.ppotseluev.algorate.math.WeightedPoint
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.RecursiveCachedIndicator
import org.ta4j.core.num.Num

import scala.reflect.ClassTag

class ChannelIndicator private (
    baseIndicator: AbstractIndicator[Num],
    extremumIndicator: AbstractIndicator[Option[Extremum]],
    approximator: Approximator,
    numOfPoints: Int,
    maxError: Double
) extends RecursiveCachedIndicator[ChannelState](extremumIndicator.getBarSeries) {

  private def collectExtremums[T <: Extremum](
      index: Int,
      count: Int
  )(implicit tag: ClassTag[T]): List[T] = {
    LazyList
      .from(index, -1)
      .takeWhile(_ >= 0)
      .flatMap { idx =>
        extremumIndicator.getValue(idx) match {
          case Some(tag(value)) => Some(value)
          case _                => None
        }
      }
      .take(count)
      .toList
      .reverse
  }

  private def approximate[T <: Extremum](points: List[T]): Option[Approximation] = {
    val weightedPoints = points.map { extremum =>
      WeightedPoint(1, extremum.index, extremum.value.doubleValue)
    }
    NonEmptyList
      .fromList(weightedPoints)
      .filter(_.size >= numOfPoints)
      .map(approximator.approximate)
  }

  private def calc[T <: Extremum: ClassTag](index: Int): Option[(Num, Approximation)] = {
    val points = collectExtremums[T](index, numOfPoints)
    approximate(points).collect {
      case appr if appr.cost <= maxError =>
        numOf(appr.func.value(index.doubleValue)) -> appr
    }
  }

  private def isFit(
      channel: Channel,
      point: Extremum
  ): Boolean = {
    val appr = point match {
      case _: Extremum.Min => channel.lowerBoundApproximation
      case _: Extremum.Max => channel.upperBoundApproximation
    }
    val p = WeightedPoint(1, point.index, point.value.doubleValue) //todo use weight?
    val cost = approximator.cost(appr, p)
    cost <= maxError
  }

  private def isInsideChannel(channel: Channel, point: (Int, Double)): Boolean = { //todo
    val (index, value) = point
    value <= channel.upperBoundApproximation.func.value(index) &&
    value >= channel.lowerBoundApproximation.func.value(index)
  }

  private def calcNewChannel(index: Int): Option[Channel] =
    for {
      (lowerBound, lowerAppr) <- calc[Extremum.Min](index)
      (upperBound, upperAppr) <- calc[Extremum.Max](index)
      section = Section(lowerBound = lowerBound, upperBound = upperBound)
      bounds = Bounds(lower = lowerAppr, upper = upperAppr)
    } yield Channel(section, bounds)

  override protected def calculate(index: Int): ChannelState = {
    def actualizeLastChannel(channel: Channel): ChannelState = {
      val lastMin = collectExtremums[Extremum.Min](index, 1).head
      val lastMax = collectExtremums[Extremum.Max](index, 1).head
//      isInsideChannel(channel, lastMin) && isInsideChannel(channel, lastMax) || TODO
      val lastExtrFit = isFit(channel, lastMin) && isFit(channel, lastMax)
      val curValue = baseIndicator.getValue(index)
      val curPoint = index -> curValue.doubleValue
      val curPointFit = isInsideChannel(channel, curPoint) ||
        isFit(channel, Extremum.Min(curValue, index)) ||
        isFit(channel, Extremum.Max(curValue, index))
      if (lastExtrFit && curPointFit) {
        val updated = channel.copy(
          section = Section(
            lowerBound = numOf(channel.lowerBoundApproximation.func.value(index)),
            upperBound = numOf(channel.upperBoundApproximation.func.value(index))
          )
        )
        CalculatedChannel(updated)
      } else {
        NeedNewChannel(channel.bounds)
      }
    }
    if (index == 0) {
      ChannelIndicator.Empty
    } else {
      getValue(index - 1) match {
        case state @ NeedNewChannel(last) =>
          calcNewChannel(index) match {
            case Some(value) =>
              if (value.bounds != last) {
                CalculatedChannel(value)
              } else {
                state
              }
            case None =>
              ChannelIndicator.Empty
          }
        case CalculatedChannel(channel) => actualizeLastChannel(channel)
        case ChannelIndicator.Empty =>
          calcNewChannel(index) match {
            case Some(value) => CalculatedChannel(value)
            case None        => ChannelIndicator.Empty
          }
      }
    }
  }
}

object ChannelIndicator {
  def apply(
      baseIndicator: AbstractIndicator[Num],
      extremumIndicator: AbstractIndicator[Option[Extremum]],
      approximator: Approximator,
      numOfPoints: Int,
      maxError: Double
  ): AbstractIndicator[Option[Channel]] = {
    val impl: AbstractIndicator[ChannelState] = new ChannelIndicator(
      baseIndicator,
      extremumIndicator,
      approximator,
      numOfPoints,
      maxError
    )
    impl.map {
      case NeedNewChannel(_) | Empty  => None
      case CalculatedChannel(channel) => Some(channel)
    }
  }

  sealed trait ChannelState
  case class NeedNewChannel(last: Bounds) extends ChannelState
  case class CalculatedChannel(channel: Channel) extends ChannelState
  case object Empty extends ChannelState

  case class Section(lowerBound: Num, upperBound: Num)

  case class Bounds(
      lower: Approximation,
      upper: Approximation
  )

  case class Channel(
      section: Section,
      bounds: Bounds
  ) {
    def upperBoundApproximation: Approximation = bounds.upper

    def lowerBoundApproximation: Approximation = bounds.lower
  }
}
