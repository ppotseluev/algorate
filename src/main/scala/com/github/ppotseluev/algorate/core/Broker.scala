package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.model.{InstrumentId, Order, OrderId, Ticker}
import ru.tinkoff.piapi.contract.v1.Share

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.stream.Collectors
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

trait Broker[F[_]] {
  def getShare(ticker: Ticker): F[Share]

  def getAllShares: F[List[Share]] //TODO abstract over tinkoff model

  def placeOrder(order: Order): F[OrderId]

  def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[List[Bar]]
}

object Broker {
  case class Day(localDate: LocalDate) {
    val start: Instant = localDate.atStartOfDay.toInstant(ZoneOffset.UTC)
    val end: Instant = localDate.plusDays(1).atStartOfDay.toInstant(ZoneOffset.UTC)
    val id: Long = start.getEpochSecond
  }

  case class DaysInterval(start: LocalDate, end: LocalDate) {
    require(!start.isAfter(end), "start can't be after end")

    def iterate: List[Day] =
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
