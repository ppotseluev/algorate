package com.github.ppotseluev.algorate.ta4j.indicator

import cats.data.NonEmptyList
import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.{Channel, Section}
import com.github.ppotseluev.algorate.ta4j.indicator.LocalExtremumIndicator.Extremum
import com.github.ppotseluev.algorate.util.Approximator
import com.github.ppotseluev.algorate.util.Approximator.Approximation
import org.apache.commons.math3.fitting.WeightedObservedPoint
import org.ta4j.core.indicators.{AbstractIndicator, CachedIndicator}
import org.ta4j.core.num.Num

import scala.collection.mutable
import scala.reflect.ClassTag

class ChannelIndicator(
    extremumIndicator: AbstractIndicator[Option[Extremum]],
    approximator: Approximator,
    numOfPoints: Int,
    maxError: Double
) extends CachedIndicator[Option[Channel]](extremumIndicator) {

  private val channelCache: mutable.ArrayBuffer[Option[Channel]] =
    mutable.ArrayBuffer.empty

  private def collectExtremums[T <: Extremum](
      index: Int,
      count: Int
  )(implicit tag: ClassTag[T]): List[(Int, T)] = {
    LazyList
      .from(index, -1)
      .takeWhile(_ >= 0)
      .flatMap { idx =>
        extremumIndicator.getValue(idx) match {
          case Some(tag(value)) => Some(idx -> value)
          case _                => None
        }
      }
      .take(count)
      .reverse
      .toList
  }

  private def approximate[T <: Extremum](points: List[(Int, T)]): Option[Approximation] = {
    val weightedPoints = points.map { case (idx, extremum) =>
      new WeightedObservedPoint(1, idx, extremum.value.doubleValue)
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
      point: (Int, Extremum)
  ): Boolean = {
    val appr = point._2 match {
      case _: Extremum.Min => channel.lowerBoundApproximation
      case _: Extremum.Max => channel.uppperBoundApproximation
    }
    val p = new WeightedObservedPoint(1, point._1, point._2.value.doubleValue)
    val cost = approximator.cost(appr, p)
    cost <= maxError
  }

  private def calcNewChannel(index: Int): Option[Channel] =
    for {
      (lowerBound, lowerAppr) <- calc[Extremum.Min](index)
      (upperBound, upperAppr) <- calc[Extremum.Max](index)
      section = Section(lowerBound, upperBound)
    } yield Channel(section, lowerAppr, upperAppr)

  override def calculate(index: Int): Option[Channel] = {
    val prevChannel =
      if (index >= 1) {
        channelCache(index - 1)
      } else {
        None
      }
    def isChannelFit(channel: Channel): Boolean = {
      val lastMin = collectExtremums[Extremum.Min](index, 1)
      val lastMax = collectExtremums[Extremum.Max](index, 1)
      isFit(channel, lastMin.head) && isFit(channel, lastMax.head)
    }
    val newChannel = prevChannel match {
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
    channelCache += newChannel
    newChannel
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
