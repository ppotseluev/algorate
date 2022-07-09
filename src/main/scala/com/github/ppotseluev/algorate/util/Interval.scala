package com.github.ppotseluev.algorate.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import scala.collection.immutable.NumericRange

case class Interval[T, N](from: T, to: T)(implicit num: Integral[N], val bc: BiConverter[T, N]) {
  def toRange: NumericRange[N] = NumericRange.inclusive(
    bc.applyA(from),
    bc.applyA(to),
    step = num.one
  )

  def map(f: T => T): Interval[T, N] = copy(f(from), f(to))
}

object Interval {
  type Time = Interval[OffsetDateTime, Long]

  def minutes(from: OffsetDateTime, to: OffsetDateTime): Interval.Time = {
    require(ZoneId.from(from) == ZoneId.from(to))
    Interval(from, to)(
      implicitly,
      BiConverter(
        _.toEpochSecond / 60,
        minutes => OffsetDateTime.ofInstant(Instant.ofEpochSecond(minutes * 60), ZoneId.from(from))
      )
    )
  }

}
