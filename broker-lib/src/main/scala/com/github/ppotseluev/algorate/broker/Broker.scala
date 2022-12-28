package com.github.ppotseluev.algorate.broker

import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.Order
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.stream.Collectors
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

trait Broker[F[_]] {
  def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo]

  def placeOrder(order: Order): F[OrderPlacementInfo]

  def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[List[Bar]]
}

object Broker {
  sealed trait OrderExecutionStatus
  object OrderExecutionStatus {
    case object Completed extends OrderExecutionStatus
    case object Failed extends OrderExecutionStatus
    case object Pending extends OrderExecutionStatus
  }

  case class OrderPlacementInfo(
      orderId: OrderId,
      status: OrderExecutionStatus
  )

  case class Day(localDate: LocalDate) {
    val start: Instant = localDate.atStartOfDay.toInstant(ZoneOffset.UTC)
    val end: Instant = localDate.plusDays(1).atStartOfDay.toInstant(ZoneOffset.UTC)
    val id: Long = start.getEpochSecond
  }

  case class DaysInterval(start: LocalDate, end: LocalDate) {
    require(!start.isAfter(end), "start can't be after end")

    def days: List[Day] =
      start
        .datesUntil(end.plusDays(1))
        .collect(Collectors.toList[LocalDate])
        .asScala
        .toList
        .map(Day)
  }
  object DaysInterval {
    def singleDay(day: Day): DaysInterval = DaysInterval(day.localDate, day.localDate)
  }

  case class CandlesInterval(
      interval: DaysInterval,
      resolution: CandleResolution
  )

  sealed trait CandleResolution {
    def duration: FiniteDuration
  }
  object CandleResolution {
    case object OneMinute extends CandleResolution {
      override def duration: FiniteDuration = 1.minute
    }
  }
}