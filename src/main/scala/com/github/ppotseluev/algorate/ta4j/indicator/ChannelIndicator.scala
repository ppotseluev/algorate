package com.github.ppotseluev.algorate.ta4j.indicator

import cats.data.NonEmptyList
import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.{Channel, Section}
import com.github.ppotseluev.algorate.ta4j.indicator.LastLocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.util.Approximator.Approximation
import com.github.ppotseluev.algorate.util.{Approximator, WeightedPoint}
import org.ta4j.core.indicators.{AbstractIndicator, RecursiveCachedIndicator}
import org.ta4j.core.num.Num

import scala.reflect.ClassTag

class ChannelIndicator(
    extremumIndicator: AbstractIndicator[Option[Extremum]],
    approximator: Approximator,
    numOfPoints: Int,
    maxError: Double //TODO normalize it in some way...
) extends RecursiveCachedIndicator[Option[Channel]](extremumIndicator.getBarSeries) {

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
      case _: Extremum.Max => channel.uppperBoundApproximation
    }
    val p = WeightedPoint(1, point.index, point.value.doubleValue) //todo use weight?
    val cost = approximator.cost(appr, p)
    cost <= maxError
  }

  private def isInsideChannel(channel: Channel, point: Extremum): Boolean = { //todo
    val value = point.value.doubleValue
    value <= channel.uppperBoundApproximation.func.value(point.index) &&
    value >= channel.lowerBoundApproximation.func.value(point.index)
  }

  private def calcNewChannel(index: Int): Option[Channel] =
    for {
      (lowerBound, lowerAppr) <- calc[Extremum.Min](index)
      (upperBound, upperAppr) <- calc[Extremum.Max](index)
      section = Section(lowerBound, upperBound)
    } yield Channel(section, lowerAppr, upperAppr)

  override protected def calculate(index: Int): Option[Channel] = {
    def isChannelFit(channel: Channel): Boolean = {
      val lastMin = collectExtremums[Extremum.Min](index, 1).head
      val lastMax = collectExtremums[Extremum.Max](index, 1).head
//      isInsideChannel(channel, lastMin) && isInsideChannel(channel, lastMax) || TODO
      isFit(channel, lastMin) && isFit(channel, lastMax)
    }
    if (index == 0) {
      None
    } else {
      val prevChannel: Option[Channel] = getValue(index - 1)
      prevChannel match {
        case Some(channel) if isChannelFit(channel) =>
          val updated = channel.copy(
            section = Section(
              lowerBound = numOf(channel.lowerBoundApproximation.func.value(index)),
              upperBound = numOf(channel.uppperBoundApproximation.func.value(index))
            )
          )
          Some(updated)
        case _ =>
          calcNewChannel(index)
      }
    }
  }
}

object ChannelIndicator {
  case class Section(lowerBound: Num, upperBound: Num)

  case class Channel(
      section: Section,
      lowerBoundApproximation: Approximation,
      uppperBoundApproximation: Approximation
  )
}
