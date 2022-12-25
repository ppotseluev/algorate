package com.github.ppotseluev.algorate.broker

import cats.Functor
import cats.syntax.functor._
import com.github.ppotseluev.algorate.broker.Broker.{CandlesInterval, OrderPlacementInfo}
import com.github.ppotseluev.algorate.model._
import ru.tinkoff.piapi.contract.v1.{OrderExecutionReportStatus, OrderState, Share}

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.stream.Collectors
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

trait Broker[F[_]] {
  final def getShare(ticker: Ticker)(implicit F: Functor[F]): F[Share] =
    getAllShares
      .map(_.filter(_.getTicker == ticker))
      .map { relatedShares =>
        require(relatedShares.size == 1, s"${relatedShares.size} shares found for ticker $ticker")
        relatedShares.head
      }

  def getOrderState(orderId: OrderId): F[OrderState]

  def getAllShares: F[List[Share]] //TODO abstract over tinkoff model

  def placeOrder(order: Order): F[OrderPlacementInfo]

  def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[List[Bar]]
}

object Broker {
  case class OrderPlacementInfo(
      orderId: OrderId,
      status: OrderExecutionReportStatus
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
