package com.github.ppotseluev.algorate.tools.backtesting

import cats.implicits._
import com.github.ppotseluev.algorate.broker.Broker.{
  CandleResolution,
  CandlesInterval,
  DaysInterval
}

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

case class Period(year: Int, range: Option[(MonthDay, MonthDay)] = None) {
  private val startMonth = range.fold(MonthDay.of(1, 1))(_._1)
  private val endMonth = range.fold(MonthDay.of(12, 31))(_._2)

  def start: LocalDate = startMonth.atYear(year)
  def end: LocalDate = endMonth.atYear(year)
  def toInterval: DaysInterval = DaysInterval(start, end)
  def toCandlesInterval(resolution: CandleResolution): CandlesInterval =
    CandlesInterval(toInterval, resolution)

  def splitMonthly: List[Period] =
    (startMonth.getMonthValue to endMonth.getMonthValue).map { m =>
      val month = Month.of(m)
      Period(
        year,
        (MonthDay.of(month, 1), MonthDay.of(month, month.minLength)).some
      )
    }.toList

}

object Period {
  def firstHalf(year: Int): Period =
    Period(year, (MonthDay.of(1, 1) -> MonthDay.of(6, 30)).some)

  def secondHalf(year: Int): Period =
    Period(year, (MonthDay.of(7, 1) -> MonthDay.of(12, 31)).some)
}
