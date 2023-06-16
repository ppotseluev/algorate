package com.github.ppotseluev.algorate.broker.tinkoff

import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import io.github.paoloboni.binance.common.Interval
import io.github.paoloboni.binance.common.response.KLineStream

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.concurrent.duration.FiniteDuration

object BinanceConverters {
  val subscriptionInterval: CandleResolution => Interval = {
    case CandleResolution.OneMinute  => Interval.`1m`
    case CandleResolution.FiveMinute => Interval.`5m`
    case CandleResolution.Minutes(_) => ???
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
}
