package com.github.ppotseluev.algorate.tinkoff

import cats.effect.kernel.Async
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import com.github.ppotseluev.algorate.core.{Broker, Point}
import com.github.ppotseluev.algorate.model._
import com.github.ppotseluev.algorate.util.{Interval, fromJavaFuture}
import com.softwaremill.tagging._
import fs2.Stream
import fs2.interop.reactivestreams
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.model.rest.{CandleResolution, Candles, LimitOrderRequest, MarketOrderRequest, OperationType => TinkoffOperationType}
import ru.tinkoff.invest.openapi.model.streaming.{CandleInterval, StreamingEvent, StreamingRequest}

import java.time.OffsetDateTime
import scala.jdk.CollectionConverters._

//TODO hard-coded candles interval 1min
class TinkoffBroker[F[_]](
    tinkoffClient: OpenApi,
    brokerAccountId: BrokerAccountId
)(implicit F: Async[F])
    extends Broker[F] {

  override def placeOrder(order: Order): F[OrderId] = {
    val operationType = tinkoffOperation(order)
    order.details match {
      case Order.Details.Limit(price) =>
        val request = new LimitOrderRequest()
        request.setLots(order.lots)
        request.setPrice(BigDecimal(price).bigDecimal)
        request.setOperation(operationType)
        fromJavaFuture(
          tinkoffClient.getOrdersContext.placeLimitOrder(
            order.instrumentId,
            request,
            brokerAccountId
          )
        ).map(_.getOrderId.taggedWith[Tags.OrderId])
      case Order.Details.Market(_) =>
        val request = new MarketOrderRequest()
        request.setLots(order.lots)
        request.setOperation(operationType)
        fromJavaFuture(
          tinkoffClient.getOrdersContext.placeMarketOrder(
            order.instrumentId,
            request,
            brokerAccountId
          )
        ).map(_.getOrderId.taggedWith[Tags.OrderId])
    }
  }

  private def tinkoffOperation(order: Order): TinkoffOperationType =
    order.operationType match {
      case OperationType.Buy  => TinkoffOperationType.BUY
      case OperationType.Sell => TinkoffOperationType.SELL
    }

  override def getData(
      instrumentId: InstrumentId,
      interval: Option[Interval[OffsetDateTime]]
  ): F[Stream[F, Point]] = interval match {
    case Some(value) =>
      getHistoricalData(instrumentId, value)
    case None =>
      subscribe(instrumentId)
  }

  private def getHistoricalData(
      instrumentId: InstrumentId,
      interval: Interval[OffsetDateTime]
  ): F[Stream[F, Point]] =
    fromJavaFuture(
      tinkoffClient.getMarketContext.getMarketCandles(
        instrumentId,
        interval.from,
        interval.to,
        CandleResolution._1MIN
      )
    ).map(o => Option(o.orElse(null)))
      .flatMap(makeStream(instrumentId))

  private def makeStream(
      instrumentId: InstrumentId
  )(maybeCandles: Option[Candles]): F[Stream[F, Point]] =
    maybeCandles match {
      case Some(value) =>
        val candles = value.getCandles.asScala.map { candle =>
          Point(candle.getTime, candle.getO.doubleValue.taggedWith[Tags.Price])
        }
        Stream.emits(candles).evalMap(_.pure).pure
      case None =>
        F.raiseError(new NoSuchElementException(s"Instrument not found: $instrumentId"))
    }

  private def subscribe(
      instrumentId: InstrumentId
  ): F[Stream[F, Point]] = {
    val subscribeRequest = StreamingRequest.subscribeCandle(
      instrumentId,
      CandleInterval._1MIN
    )
    for {
      _ <- F.delay(tinkoffClient.getStreamingContext.sendRequest(subscribeRequest))
      stream = reactivestreams.fromPublisher[F, StreamingEvent](
        tinkoffClient.getStreamingContext,
        bufferSize = 1
      )
    } yield stream.evalMapFilter {
      case candle: StreamingEvent.Candle =>
        Point(
          OffsetDateTime.now,
          candle.getClosingPrice.doubleValue.taggedWith[Tags.Price]
        ).some.pure
      case _ => Option.empty[Point].pure
    }
  }
}
