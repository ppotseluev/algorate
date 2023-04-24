package com.github.ppotseluev.algorate.tools.backtesting

import cats.implicits._
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

case class Period(year: Int, range: Option[(MonthDay, MonthDay)] = None) {
  private val startMonth = range.fold(MonthDay.of(1, 1))(_._1)
  private val endMonth = range.fold(MonthDay.of(12, 31))(_._2)

  def start: LocalDate = startMonth.atYear(year)
  def end: LocalDate = endMonth.atYear(year)
  def toInterval: DaysInterval = DaysInterval(start, end)

  def splitMonthly: List[Period] =
    (startMonth.getMonthValue to endMonth.getMonthValue).map { m =>
      val month = Month.of(m)
      Period(
        year,
        (MonthDay.of(month, 1), MonthDay.of(month, month.minLength)).some
      )
    }.toList

}
