package com.github.ppotseluev.algorate

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration

case class Bar(
    openPrice: Price,
    closePrice: Price,
    lowPrice: Price,
    highPrice: Price,
    volume: Double,
    endTime: OffsetDateTime,
    duration: FiniteDuration
)
