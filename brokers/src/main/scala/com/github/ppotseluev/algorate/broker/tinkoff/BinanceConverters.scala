package com.github.ppotseluev.algorate.broker.tinkoff

import com.binance.api.client.domain.OrderStatus
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.OperationType
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.OrderExecutionStatus
import io.github.paoloboni.binance.common.Interval
import io.github.paoloboni.binance.common.KLine
import io.github.paoloboni.binance.common.OrderSide
import io.github.paoloboni.binance.common.response.KLineStream
import io.github.paoloboni.binance.spot.SpotOrderStatus
import io.github.paoloboni.binance.spot.SpotOrderStatus._
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.duration.FiniteDuration

object BinanceConverters {
  val convert: CandleResolution => Interval = {
    case CandleResolution.OneMinute  => Interval.`1m`
    case CandleResolution.FiveMinute => Interval.`5m`
    case CandleResolution.Minutes(_) => ???
  }

  def convert(duration: FiniteDuration)(kline: KLine): Bar = {
    Bar(
      openPrice = kline.open,
      closePrice = kline.close,
      lowPrice = kline.low,
      highPrice = kline.high,
      volume = kline.volume.doubleValue,
      trades = kline.numberOfTrades,
      endTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(kline.closeTime), ZoneOffset.UTC),
      duration = duration
    )
  }

  def convert(kline: KLineStream): Bar = {
    val x = kline.k
    Bar(
      openPrice = x.o,
      closePrice = x.c,
      lowPrice = x.l,
      highPrice = x.h,
      volume = x.v.doubleValue,
      trades = x.n,
      endTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(x.T), ZoneOffset.UTC),
      duration = x.i.duration.asInstanceOf[FiniteDuration]
    )
  }

  def convert(operation: OperationType): OrderSide = operation match {
    case OperationType.Buy  => OrderSide.BUY
    case OperationType.Sell => OrderSide.SELL
  }

  def convert(status: SpotOrderStatus): OrderExecutionStatus = status match {
    case NEW | PARTIALLY_FILLED | PENDING_CANCEL => OrderExecutionStatus.Pending
    case FILLED                                  => OrderExecutionStatus.Completed
    case CANCELED | REJECTED | EXPIRED           => OrderExecutionStatus.Failed
  }

  def convert(status: OrderStatus): OrderExecutionStatus = status match {
    case OrderStatus.NEW | OrderStatus.PARTIALLY_FILLED | OrderStatus.PENDING_CANCEL =>
      OrderExecutionStatus.Pending
    case OrderStatus.FILLED =>
      OrderExecutionStatus.Completed
    case OrderStatus.CANCELED | OrderStatus.REJECTED | OrderStatus.EXPIRED =>
      OrderExecutionStatus.Failed
  }
}
