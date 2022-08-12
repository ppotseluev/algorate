package com.github.ppotseluev.algorate.model

import boopickle.Default._
import boopickle.PickleState
import boopickle.UnpickleState
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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

object Bar {
  private case class View(
      openPrice: BigDecimal,
      closePrice: BigDecimal,
      lowPrice: BigDecimal,
      highPrice: BigDecimal,
      volume: Long,
      endTimeSeconds: Long,
      endTimeNano: Int,
      duration: FiniteDuration
  ) {
    def toBar: Bar = Bar(
      openPrice = openPrice,
      closePrice = closePrice,
      lowPrice = lowPrice,
      highPrice = highPrice,
      volume = volume,
      endTime = OffsetDateTime.ofInstant(
        Instant.ofEpochSecond(endTimeSeconds).plusNanos(endTimeNano),
        ZoneOffset.UTC
      ),
      duration = duration
    )
  }

  private def toView(bar: Bar): View =
    View(
      openPrice = bar.openPrice,
      closePrice = bar.closePrice,
      lowPrice = bar.lowPrice,
      highPrice = bar.highPrice,
      volume = bar.volume,
      endTimeSeconds = bar.endTime.toEpochSecond,
      endTimeNano = bar.endTime.getNano,
      duration = bar.duration
    )

  implicit val pickler: Pickler[Bar] = new Pickler[Bar] {
    val _pickler = implicitly[Pickler[View]]

    override def pickle(obj: Bar)(implicit state: PickleState): Unit =
      _pickler.pickle(toView(obj))

    override def unpickle(implicit state: UnpickleState): Bar =
      _pickler.unpickle.toBar
  }
}
