package com.github.ppotseluev.algorate.tinkoff

import cats.Parallel
import cats.effect.kernel.Async
import cats.effect.std.UUIDGen
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import com.github.ppotseluev.algorate.core.Bar
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.model.Order.Type
import com.github.ppotseluev.algorate.model._
import com.github.ppotseluev.algorate.util.Interval.Time.Resolution
import com.github.ppotseluev.algorate.util.Interval.TimeInterval
import com.github.ppotseluev.algorate.util._
import com.google.protobuf.Timestamp
import com.softwaremill.tagging._

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.Share

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

//TODO hard-coded candles interval 1min
class TinkoffBroker[F[_]: Parallel](
    api: TinkoffApi[F],
    brokerAccountId: BrokerAccountId
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

  private def candleInterval(timeResolution: Interval.Time.Resolution): CandleInterval =
    timeResolution match {
      case Resolution.OneMinute => CandleInterval.CANDLE_INTERVAL_1_MIN
    }

  override def getData(
      instrumentId: InstrumentId,
      timeInterval: Interval.Time
  ): F[Seq[Bar]] = {
    val resolution = timeInterval.resolution
    def get(interval: TimeInterval) = {
      api
        .getCandles(
          instrumentId,
          interval.from.toInstant,
          interval.to.toInstant,
          candleInterval(resolution)
        )
        .map(
          _.map(convert(resolution.duration, interval.from.getOffset))
        )
    }
    val maxPeriod = 1.day
    val intervals = split[OffsetDateTime, Long](
      interval = timeInterval.interval,
      range = (BigDecimal(maxPeriod.toNanos) / resolution.duration.toNanos).toLongExact,
      offset = 1
    )
    intervals.parTraverse(get).map(_.flatten)
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

  override val getAllShares: F[List[Share]] =
    api.getAllShares

  override def getShare(ticker: Ticker): F[Share] =
    getAllShares
      .map(_.filter(_.getTicker == ticker))
      .map { relatedShares =>
        require(relatedShares.size == 1, s"${relatedShares.size} shares found for ticker $ticker")
        relatedShares.head
      }
}
