package com.github.ppotseluev.algorate.broker.tinkoff

import com.github.ppotseluev.algorate.math.RealNumber
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.Price
import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import ru.tinkoff.piapi.contract.v1.Candle
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

  def convert(candle: Candle, zoneId: ZoneId = ZoneOffset.UTC): Bar =
    Bar(
      openPrice = price(candle.getOpen),
      closePrice = price(candle.getClose),
      lowPrice = price(candle.getLow),
      highPrice = price(candle.getHigh),
      volume = candle.getVolume,
      endTime = fromProto(candle.getTime, zoneId),
      duration = duration(candle.getInterval)
    )

  private def duration(interval: SubscriptionInterval): FiniteDuration =
    interval match {
      case SUBSCRIPTION_INTERVAL_ONE_MINUTE   => 1.minute
      case SUBSCRIPTION_INTERVAL_FIVE_MINUTES => 5.minutes
      case UNRECOGNIZED | SUBSCRIPTION_INTERVAL_UNSPECIFIED =>
        throw new IllegalArgumentException(s"Wrong candle interval $interval")
    }
}
