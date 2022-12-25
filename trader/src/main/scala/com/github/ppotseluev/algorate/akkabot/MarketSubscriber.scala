package com.github.ppotseluev.algorate.akkabot

import cats.Id
import cats.effect.kernel.Sync
import cats.effect.kernel.Temporal
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.core.HistoryStream
import com.github.ppotseluev.algorate.model.BarInfo
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.tinkoff.TinkoffConverters
import com.typesafe.scalalogging.LazyLogging
import java.time.LocalDate
import java.util.function.Consumer
import ru.tinkoff.piapi.contract.v1.MarketDataResponse
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval
import ru.tinkoff.piapi.core.InvestApi
import ru.tinkoff.piapi.core.stream.StreamProcessor
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait MarketSubscriber[F[_], C[_]] {
  def subscribe(instrument: C[InstrumentId]): F[Unit]
}

object MarketSubscriber extends LazyLogging {

  /**
   * Actor-based subscriber
   */
  def fromActor(actor: TradingManager) =
    new FromActor(actor)

  class FromActor private[MarketSubscriber] (actor: TradingManager) {
    def using[F[_]: Sync](investApi: InvestApi): MarketSubscriber[F, List] =
      (instruments: List[InstrumentId]) =>
        Sync[F].delay {
          val streamProcessor: StreamProcessor[MarketDataResponse] =
            (data: MarketDataResponse) => {
              if (data.hasCandle) {
                val candle = data.getCandle
                val bar = TinkoffConverters.convert(candle)
                val barInfo = BarInfo(candle.getFigi, bar)
                actor ! TradingManager.Event.CandleData(barInfo)
              } else
                ()
            }
          val logErrorsHandler: Consumer[Throwable] = t => {
            logger.error("Something went wrong", t)
          }
          val stream = investApi.getMarketDataStreamService
            .newStream("market-data-stream", streamProcessor, logErrorsHandler)
          stream.subscribeCandles(
            instruments.asJava,
            SubscriptionInterval.SUBSCRIPTION_INTERVAL_ONE_MINUTE
          )
        }

    def stub[F[_]: Temporal: Sync](
        broker: Broker[F],
        streamFrom: LocalDate,
        streamTo: LocalDate,
        rate: FiniteDuration
    ): MarketSubscriber[F, Id] = (instrument: InstrumentId) =>
      HistoryStream
        .make[F](
          instrumentId = instrument,
          broker = broker,
          from = streamFrom,
          to = streamTo,
          rate = rate
        )
        .foreach { barInfo =>
          Sync[F].delay(actor ! TradingManager.Event.CandleData(barInfo))
        }
        .compile
        .drain
  }
}
