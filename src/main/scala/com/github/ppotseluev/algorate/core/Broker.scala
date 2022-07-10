package com.github.ppotseluev.algorate.core

import cats.data.NonEmptyList
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Ticker
import org.jfree.data.time.Day
import ru.tinkoff.piapi.contract.v1.Share

import java.time.ZoneId
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait Broker[F[_]] {
  def getShare(ticker: Ticker): F[Share]

  def getAllShares: F[List[Share]] //TODO abstract over tinkoff model

  def placeOrder(order: Order): F[OrderId]

  def getData(
      instrumentId: InstrumentId,
      interval: CandlesInterval
  ): F[Seq[Bar]]
}

object Broker {
  case class Interval(start: Day, end: Day) {
    require(start.getStart.before(end.getStart), "start must be before end")

    def days: NonEmptyList[Day] = {
      def loop(acc: NonEmptyList[Day]): NonEmptyList[Day] = {
        if (acc.head == start) acc
        else loop(acc.head.previous.asInstanceOf[Day] :: acc)
      }
      loop(NonEmptyList.one(end))
    }
  }
  case class CandlesInterval(
      interval: Interval,
      resolution: CandleResolution,
      zoneId: ZoneId
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
