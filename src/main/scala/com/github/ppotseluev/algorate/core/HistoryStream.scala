package com.github.ppotseluev.algorate.core

import cats.effect.kernel.Temporal
import com.github.ppotseluev.algorate.core.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.core.Broker.DaysInterval
import com.github.ppotseluev.algorate.model.BarInfo
import com.github.ppotseluev.algorate.model.InstrumentId
import fs2.Stream
import java.time.LocalDate
import scala.concurrent.duration._

/**
 * Utils to make stream from the historical data
 */
object HistoryStream {

  def make[F[_]: Temporal](
      instrumentId: InstrumentId,
      broker: Broker[F],
      from: LocalDate,
      to: LocalDate,
      rate: FiniteDuration
  ): Stream[F, BarInfo] = {
    val candlesInterval = CandlesInterval(
      interval = DaysInterval(from, to),
      resolution = OneMinute
    )
    Stream
      .eval(broker.getData(instrumentId, candlesInterval))
      .flatMap(Stream.emits(_))
      .map(BarInfo(instrumentId, _))
      .meteredStartImmediately(rate)
  }
}
