package com.github.ppotseluev.algorate.tinkoff

import com.github.ppotseluev.algorate.util._
import cats.Parallel
import cats.effect.kernel.Async
import cats.effect.std.UUIDGen
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.syntax.parallel._
import cats.syntax.traverse._
import com.github.ppotseluev.algorate.core.{Bar, Broker, Point}
import com.github.ppotseluev.algorate.model.Order.Type
import com.github.ppotseluev.algorate.model._
import com.github.ppotseluev.algorate.util.{Interval, fromJavaFuture, split}
import com.google.protobuf.Timestamp
import com.softwaremill.tagging._
import fs2.Stream
import fs2.interop.reactivestreams
import ru.tinkoff.piapi.contract.v1.{
  CandleInterval,
  HistoricCandle,
  OrderDirection,
  OrderType,
  Quotation
}
import ru.tinkoff.piapi.core.InvestApi

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.UUID
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

//TODO hard-coded candles interval 1min
class TinkoffBroker[F[_]: Parallel](
    api: InvestApi,
    brokerAccountId: BrokerAccountId
)(implicit F: Async[F])
    extends Broker[F] {

  override def placeOrder(order: Order): F[OrderId] =
    for {
      orderId <- UUIDGen.randomString //TODO make idempotent
      _ <- fromJavaFuture( //TODO handle returned result
        api.getOrdersService.postOrder(
          order.instrumentId,
          order.lots,
          price(order),
          orderDirection(order),
          brokerAccountId,
          orderType(order),
          orderId
        )
      )
    } yield orderId.taggedWith[Tags.OrderId]

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
    real.asBigDecimal.taggedWith[Tags.Price]
  }

  private def fromProto(timestamp: Timestamp, zoneId: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(
      Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos),
      zoneId
    )

  private def convert(candleDuration: FiniteDuration, zoneId: ZoneId)(candle: HistoricCandle): Bar =
    Bar(
      openPrice = price(candle.getOpen),
      closePrice = price(candle.getClose),
      lowPrice = price(candle.getLow),
      highPrice = price(candle.getHigh),
      volume = candle.getVolume,
      endTime = fromProto(candle.getTime, zoneId),
      duration = candleDuration //todo candle can be not closed
    )

  override def getData(
      instrumentId: InstrumentId,
      interval: Interval.Time
  ): F[Seq[Bar]] = {
    def get(interval: Interval.Time) = {
      val futureResult = api.getMarketDataService.getCandles(
        instrumentId,
        interval.from.toInstant,
        interval.to.toInstant,
        CandleInterval.CANDLE_INTERVAL_1_MIN
      )
      fromJavaFuture(futureResult)
        .map(
          _.asScala.toSeq.map(convert(1.minute, interval.from.getOffset))
        )
    }
    val intervals = split[OffsetDateTime, Long](
      interval = interval,
      range = 1.day.toMinutes,
      offset = 1
    )
    //   TODO intervals.parTraverse(get).map(_.flatten)
    intervals.traverse(get).map(_.flatten)
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

}
