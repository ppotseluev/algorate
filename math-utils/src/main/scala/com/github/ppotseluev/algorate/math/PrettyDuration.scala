package com.github.ppotseluev.algorate.math

import scala.annotation.tailrec

import concurrent.duration._

object PrettyDuration {
  val timeUnitList: List[TimeUnit] =
    DAYS ::
      HOURS ::
      MINUTES ::
      SECONDS ::
      MILLISECONDS ::
      MICROSECONDS ::
      NANOSECONDS ::
      Nil

  implicit class PrettyPrintableDuration(val duration: Duration) extends AnyVal {

    @tailrec
    private def prettyRec(
        acc: List[FiniteDuration],
        remUnits: List[TimeUnit],
        rem: FiniteDuration,
        isPast: Boolean
    ): String = {
      remUnits match {
        case h :: t =>
          if (rem >= Duration(1, h)) {
            val x = Duration(rem.toUnit(h).toLong, h)
            prettyRec(x :: acc, t, rem - x, isPast)
          } else {
            prettyRec(acc, t, rem, isPast)
          }
        case Nil =>
          acc.reverse.map(_.toString).mkString(" ") + (if (isPast) " ago" else "")
      }
    }

    def pretty: String = {
      duration match {
        case Duration.Zero => "now"
        case f: FiniteDuration if f < Duration.Zero =>
          prettyRec(Nil, timeUnitList, f * -1, isPast = true)
        case f: FiniteDuration => prettyRec(Nil, timeUnitList, f, isPast = false)
        case Duration.Inf      => "infinite"
        case Duration.MinusInf => "minus infinite"
        case _                 => "undefined"
      }
    }
  }
}
