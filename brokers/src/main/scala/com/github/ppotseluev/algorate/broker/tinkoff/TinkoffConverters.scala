package com.github.ppotseluev.algorate.broker.tinkoff

import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.Price
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus
import com.github.ppotseluev.algorate.math.RealNumber
import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import ru.tinkoff.piapi.contract.v1.Candle
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus._
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval._
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object TinkoffConverters {
  private[tinkoff] def fromProto(timestamp: Timestamp, zoneId: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(
      Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos),
      zoneId
    )

  def price(quotation: Quotation): Price = {
    val real = RealNumber(quotation.getUnits, quotation.getNano)
    real.asBigDecimal
  }

  def convert(status: OrderExecutionReportStatus): OrderExecutionStatus = status match {
    case EXECUTION_REPORT_STATUS_FILL =>
      OrderExecutionStatus.Completed
    case EXECUTION_REPORT_STATUS_REJECTED | EXECUTION_REPORT_STATUS_CANCELLED =>
      OrderExecutionStatus.Failed
    case EXECUTION_REPORT_STATUS_NEW | EXECUTION_REPORT_STATUS_PARTIALLYFILL =>
      OrderExecutionStatus.Pending
    case s @ (EXECUTION_REPORT_STATUS_UNSPECIFIED | OrderExecutionReportStatus.UNRECOGNIZED) =>
      throw new IllegalStateException(s"Wrong order status $s")
  }

  def convert(candle: Candle, zoneId: ZoneId = ZoneOffset.UTC): Bar =
    Bar(
      openPrice = price(candle.getOpen),
      closePrice = price(candle.getClose),
      lowPrice = price(candle.getLow),
      highPrice = price(candle.getHigh),
      volume = candle.getVolume.toDouble,
      endTime = fromProto(candle.getTime, zoneId),
      duration = duration(candle.getInterval)
    )

  private def duration(interval: SubscriptionInterval): FiniteDuration =
    interval match {
      case SUBSCRIPTION_INTERVAL_ONE_MINUTE   => 1.minute
      case SUBSCRIPTION_INTERVAL_FIVE_MINUTES => 5.minutes
      case SubscriptionInterval.UNRECOGNIZED | SUBSCRIPTION_INTERVAL_UNSPECIFIED =>
        throw new IllegalArgumentException(s"Wrong candle interval $interval")
    }
}
