package com.github.ppotseluev.algorate.trader.app

import cats.Id
import cats.effect.kernel.Sync
import cats.effect.kernel.Temporal
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffConverters
import com.github.ppotseluev.algorate.trader.HistoryStream
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager
import com.typesafe.scalalogging.LazyLogging
import java.time.LocalDate
import java.util.function.Consumer
import ru.tinkoff.piapi.contract.v1.MarketDataResponse
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval
import ru.tinkoff.piapi.contract.v1.SubscriptionInterval._
import ru.tinkoff.piapi.core.InvestApi
import ru.tinkoff.piapi.core.stream.StreamProcessor
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait MarketSubscriber[F[_], C[_]] {
  def subscribe(instrument: C[TradingAsset]): F[Unit]
}

object MarketSubscriber extends LazyLogging {

  /**
   * Actor-based subscriber
   */
  def fromActor(actor: TradingManager, candleResolution: CandleResolution) =
    new FromActor(actor, candleResolution)

  private val subscriptionInterval: CandleResolution => SubscriptionInterval = {
    case CandleResolution.OneMinute  => SUBSCRIPTION_INTERVAL_ONE_MINUTE
    case CandleResolution.FiveMinute => SUBSCRIPTION_INTERVAL_FIVE_MINUTES
    case CandleResolution.Minutes(_) => ???
  }

  class FromActor private[MarketSubscriber] (
      actor: TradingManager,
      candleResolution: CandleResolution
  ) {
    def using[F[_]: Sync](investApi: InvestApi): MarketSubscriber[F, List] =
      (assets: List[TradingAsset]) =>
        Sync[F].delay {
          logger.info(
            s"Subscribing to ${assets.size} assets: ${assets.map(_.ticker).mkString("\n")}"
          )
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
          val instruments = assets.map(_.instrumentId)
          def makeStreamAndSubscribe(): Unit = {
            val stream = investApi.getMarketDataStreamService.newStream(
              "market-data-stream",
              streamProcessor,
              logErrorsHandler
            )
            stream.subscribeCandles(
              instruments.asJava,
              subscriptionInterval(candleResolution)
            )
          }
          def logErrorsHandler: Consumer[Throwable] = t => {
            logger.error("Something went wrong, trying to re-subscribe", t)
            makeStreamAndSubscribe()
          }
          makeStreamAndSubscribe()
        }

    def stub[F[_]: Temporal: Sync](
        broker: Broker[F],
        streamFrom: LocalDate,
        streamTo: LocalDate,
        rate: FiniteDuration
    ): MarketSubscriber[F, Id] = (asset: TradingAsset) =>
      HistoryStream
        .make[F](
          asset = asset,
          broker = broker,
          candlesInterval = CandlesInterval(
            interval = DaysInterval(streamFrom, streamTo),
            resolution = candleResolution
          ),
          rate = rate
        )
        .foreach { barInfo =>
          Sync[F].delay(actor ! TradingManager.Event.CandleData(barInfo))
        }
        .compile
        .drain
  }
}
