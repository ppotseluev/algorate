package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.Price

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration

case class Bar(
    openPrice: Price,
    closePrice: Price,
    lowPrice: Price,
    highPrice: Price,
    volume: Long,
    endTime: OffsetDateTime,
    duration: FiniteDuration
)
