package com.github.ppotseluev.algorate.strategy.indicator

import cats.data.NonEmptyList
import cats.implicits._
import com.github.ppotseluev.algorate.math.Approximator
import com.github.ppotseluev.algorate.math.Approximator.Approximation
import com.github.ppotseluev.algorate.math.WeightedPoint
import com.github.ppotseluev.algorate.strategy.indicator.ExtremumCollector._
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.indicators.RecursiveCachedIndicator
import org.ta4j.core.num.Num
import scala.reflect.ClassTag

import ChannelIndicator.CalculatedChannel
import ChannelIndicator.Channel
import ChannelIndicator.ChannelState
import ChannelIndicator.NeedNewChannel
import LocalExtremumIndicator.Extremum

class ChannelIndicator private (
    baseIndicator: AbstractIndicator[Num],
    extremumIndicator: AbstractIndicator[Option[Extremum]],
    approximator: Approximator,
    numOfPoints: Int,
    maxError: Double,
    maxBreakError: Double,
    mergeExtremumsWithin: Int = 1
) extends RecursiveCachedIndicator[ChannelState](extremumIndicator.getBarSeries) {

  private val extrMerge: Merge[Extremum] = (x, y) =>
    if (math.abs(x.index - y.index) <= mergeExtremumsWithin)
      List(x, y).maxByOption(_.index)
    else
      None

  private implicit val minMerge: Merge[Extremum.Min] = extrMerge.asInstanceOf[Merge[Extremum.Min]]
  private implicit val maxMerge: Merge[Extremum.Max] = extrMerge.asInstanceOf[Merge[Extremum.Max]]

  private def collectExtremums[T <: Extremum: Merge](
      index: Int,
      count: Int
  )(implicit tag: ClassTag[T]): List[T] = {
    val source = LazyList
      .from(index, -1)
      .takeWhile(_ >= 0)
      .flatMap { idx =>
        extremumIndicator.getValue(idx) match {
          case Some(tag(value)) => Some(value)
          case _                => None
        }
      }
    ExtremumCollector.mergeCollectReverse(source, count)
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

  private def calc[T <: Extremum: ClassTag: Merge](
      index: Int
  ): Option[(List[T], Num, Approximation)] = {
    val points = collectExtremums[T](index, numOfPoints)
    approximate(points).collect {
      case appr if appr.cost <= maxError =>
        (points, numOf(appr.func.value(index.doubleValue)), appr)
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
    cost <= maxBreakError
  }

  private def isInsideChannel(channel: Channel, point: (Int, Double)): Boolean = { //todo
    val (index, value) = point
    value <= channel.upperBoundApproximation.func.value(index) &&
    value >= channel.lowerBoundApproximation.func.value(index)
  }

  private def extrBounds(extr: List[Extremum]): Bounds[List[Extremum]] = {
    val minExtremums = extr
      .collect { case min: Extremum.Min => min }
      .sortBy(_.index)
      .distinctBy(_.index)
    val maxExtremums = extr
      .collect { case max: Extremum.Max => max }
      .sortBy(_.index)
      .distinctBy(_.index)
    Bounds(lower = minExtremums, upper = maxExtremums)
  }

  private def calcNewChannel(index: Int): Option[Channel] =
    for {
      (minExtr, lowerBound, lowerAppr) <- calc[Extremum.Min](index)
      (maxExtr, upperBound, upperAppr) <- calc[Extremum.Max](index)
      section = Bounds(lower = lowerBound, upper = upperBound)
      bounds = Bounds(lower = lowerAppr, upper = upperAppr)
      List(minStartIndex, maxStartIndex) = List(lowerAppr, upperAppr)
        .map(_.points.head.x.toInt)
        .sorted
      uncountedExtremums = (minStartIndex to maxStartIndex).flatMap(extremumIndicator.getValue)
      extremums = extrBounds(minExtr ++ maxExtr ++ uncountedExtremums)
      channel = Channel(section, bounds, extremums, index)
      if uncountedExtremums.forall(isFit(channel, _))
    } yield channel

  override protected def calculate(index: Int): ChannelState = {
    def actualizeLastChannel(channel: Channel): ChannelState = {
      val lastMin = collectExtremums[Extremum.Min](index, 1).headOption
      val lastMax = collectExtremums[Extremum.Max](index, 1).headOption
//      isInsideChannel(channel, lastMin) && isInsideChannel(channel, lastMax) || TODO
      // extremums inside channel probably shouldn't break it. But there is a problem with divergent channels
      // they can become infinite in this case
      val lastExtrFit = lastMin.exists(isFit(channel, _)) && lastMax.exists(isFit(channel, _))
      val curValue = baseIndicator.getValue(index)
      val curPoint = index -> curValue.doubleValue
      val curPointFit = isInsideChannel(channel, curPoint) ||
        isFit(channel, Extremum.Min(curValue, index)) ||
        isFit(channel, Extremum.Max(curValue, index))
      if (lastExtrFit && curPointFit) {
        val updated = channel
          .copy(
            section = Bounds(
              lower = numOf(channel.lowerBoundApproximation.func.value(index)),
              upper = numOf(channel.upperBoundApproximation.func.value(index))
            )
          )
          .addExtremums(lastMin ++ lastMax)
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
      maxError: Double,
      maxBreakError: Double
  ): AbstractIndicator[Option[Channel]] = {
    val impl: AbstractIndicator[ChannelState] = new ChannelIndicator(
      baseIndicator,
      extremumIndicator,
      approximator,
      numOfPoints,
      maxError,
      maxBreakError
    )
    impl.map {
      case NeedNewChannel(_) | Empty  => None
      case CalculatedChannel(channel) => Some(channel)
    }
  }

  sealed trait ChannelState
  case class NeedNewChannel(last: ChannelBounds) extends ChannelState
  case class CalculatedChannel(channel: Channel) extends ChannelState
  case object Empty extends ChannelState

  type Section = Bounds[Num]

  type ChannelBounds = Bounds[Approximation]

  case class Channel(
      section: Section,
      bounds: ChannelBounds,
      allExtremums: Bounds[List[Extremum]],
      startIndex: Int
  ) {
    def k: Bounds[Double] = bounds.map {
      _.func
        .asInstanceOf[PolynomialFunction]
        .getCoefficients
        .last
    }

    def addExtremums(extremums: Iterable[Extremum]): Channel =
      extremums.foldLeft(this)(_ addExtremum _)

    def addExtremum(extremum: Extremum): Channel = extremum match {
      case min: Extremum.Min =>
        val updated = (allExtremums.lower :+ min).sortBy(_.index).distinctBy(_.index)
        this.copy(allExtremums = allExtremums.copy(lower = updated))
      case max: Extremum.Max =>
        val updated = (allExtremums.upper :+ max).sortBy(_.index).distinctBy(_.index)
        this.copy(allExtremums = allExtremums.copy(upper = updated))
    }

    def upperBoundApproximation: Approximation = bounds.upper

    def lowerBoundApproximation: Approximation = bounds.lower
  }
}
