package com.github.ppotseluev.algorate.tinkoff

import cats.Parallel
import cats.effect.kernel.Async
import cats.effect.std.UUIDGen
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.core.Broker.CandleResolution
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.core.Broker.Day
import com.github.ppotseluev.algorate.model
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.Order.Type
import com.github.ppotseluev.algorate.model._
import com.github.ppotseluev.algorate.util._
import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.Share
import scala.concurrent.duration.FiniteDuration

class TinkoffBroker[F[_]: Parallel](
    api: TinkoffApi[F],
    brokerAccountId: BrokerAccountId,
    zoneId: ZoneId
)(implicit F: Async[F])
    extends Broker[F] {

  override def placeOrder(order: Order): F[OrderId] =
    for {
      orderId <- UUIDGen.randomString //TODO make idempotent
      _ <- //TODO handle returned result
        api.postOrder(
          order.instrumentId,
          order.lots,
          price(order),
          orderDirection(order),
          brokerAccountId,
          orderType(order),
          orderId
        )
    } yield orderId

  private def price(order: Order): Quotation = {
    val RealNumber(units, nano) = order.price.asRealNumber
    Quotation.newBuilder
      .setUnits(units)
      .setNano(nano)
      .build
  }

  private def orderDirection(order: Order): OrderDirection =
    order.operationType match {
      case OperationType.Buy  => OrderDirection.ORDER_DIRECTION_BUY
      case OperationType.Sell => OrderDirection.ORDER_DIRECTION_SELL
    }

  private def orderType(order: Order): OrderType =
    order.details.`type` match {
      case Type.Limit  => OrderType.ORDER_TYPE_LIMIT
      case Type.Market => OrderType.ORDER_TYPE_MARKET
    }

  private def price(quotation: Quotation): Price = {
    val real = RealNumber(quotation.getUnits, quotation.getNano)
    real.asBigDecimal
  }

  private def fromProto(timestamp: Timestamp): OffsetDateTime =
    OffsetDateTime.ofInstant(
      Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos),
      zoneId
    )

  private def convert(candleDuration: FiniteDuration)(candle: HistoricCandle): Bar =
    model.Bar(
      openPrice = price(candle.getOpen),
      closePrice = price(candle.getClose),
      lowPrice = price(candle.getLow),
      highPrice = price(candle.getHigh),
      volume = candle.getVolume,
      endTime = fromProto(candle.getTime),
      duration = candleDuration //todo candle can be not closed
    )

  private def candleInterval(timeResolution: CandleResolution): CandleInterval =
    timeResolution match {
      case CandleResolution.OneMinute => CandleInterval.CANDLE_INTERVAL_1_MIN
    }

  override def getData(
      instrumentId: InstrumentId,
      candlesInterval: CandlesInterval
  ): F[List[Bar]] = {
    val resolution = candlesInterval.resolution
    def get(day: Day) = {
      api
        .getCandles(
          instrumentId,
          day.start,
          day.end,
          candleInterval(resolution)
        )
        .map(
          _.map(convert(resolution.duration))
        )
    }
    candlesInterval.interval.days.parTraverse(get).map(_.flatten)
  }

  //  private def makeStream(
  //      instrumentId: InstrumentId
  //  )(maybeCandles: Option[Candles]): F[Stream[F, Point]] =
  //    maybeCandles match {
  //      case Some(value) =>
  //        val candles = value.getCandles.asScala.map { candle =>
  //          Point(
  //            candle.getTime,
  //            candle.getO.doubleValue.taggedWith[Tags.Price]
  //          ) //todo check time <-> o/c price
  //        }
  //        Stream.emits(candles).evalMap(_.pure).pure
  //      case None =>
  //        F.raiseError(new NoSuchElementException(s"Instrument not found: $instrumentId"))
  //    }
  //
  //  private def subscribe(
  //      instrumentId: InstrumentId
  //  ): F[Stream[F, Point]] = {
  //    val subscribeRequest = StreamingRequest.subscribeCandle(
  //      instrumentId,
  //      CandleInterval._1MIN
  //    )
  //    for {
  //      _ <- F.delay(api.getStreamingContext.sendRequest(subscribeRequest))
  //      stream = reactivestreams.fromPublisher[F, StreamingEvent](
  //        api.getStreamingContext,
  //        bufferSize = 1
  //      )
  //    } yield stream.evalMapFilter {
  //      case candle: StreamingEvent.Candle =>
  //        Point(
  //          OffsetDateTime.now,
  //          candle.getClosingPrice.doubleValue.taggedWith[Tags.Price]
  //        ).some.pure
  //      case _ => Option.empty[Point].pure
  //    }
  //  }

  override def getAllShares: F[List[Share]] =
    api.getAllShares
}
