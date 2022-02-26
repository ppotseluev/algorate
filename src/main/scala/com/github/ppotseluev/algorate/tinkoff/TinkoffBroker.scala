package com.github.ppotseluev.algorate.tinkoff

import cats.Parallel
import cats.effect.kernel.Async
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.parallel._
import cats.syntax.traverse._
import com.github.ppotseluev.algorate.core.{Bar, Broker, Point}
import com.github.ppotseluev.algorate.model._
import com.github.ppotseluev.algorate.util.{Interval, fromJavaFuture, split}
import com.softwaremill.tagging._
import fs2.Stream
import fs2.interop.reactivestreams
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.model.rest.{CandleResolution, Candles, LimitOrderRequest, MarketOrderRequest, OperationType => TinkoffOperationType}
import ru.tinkoff.invest.openapi.model.streaming.{CandleInterval, StreamingEvent, StreamingRequest}

import java.time.OffsetDateTime
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

//TODO hard-coded candles interval 1min
class TinkoffBroker[F[_]: Parallel](
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
      case Order.Details.Market =>
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

  private def convert(candles: Candles): Seq[Bar] =
    candles.getCandles.asScala.map { candle =>
      Bar(
        openPrice = candle.getO.doubleValue.taggedWith[Tags.Price],
        closePrice = candle.getC.doubleValue.taggedWith[Tags.Price],
        lowPrice = candle.getL.doubleValue.taggedWith[Tags.Price],
        highPrice = candle.getH.doubleValue.taggedWith[Tags.Price],
        volume = candle.getV,
        endTime = candle.getTime,
        duration = Duration(candle.getInterval.getValue).asInstanceOf[FiniteDuration]
      )
    }.toSeq

  override def getData(
      instrumentId: InstrumentId,
      interval: Interval.Time
  ): F[Seq[Bar]] = {
    def get(subInterval: Interval.Time): F[Seq[Bar]] = fromJavaFuture(
      tinkoffClient.getMarketContext.getMarketCandles(
        instrumentId,
        subInterval.from,
        subInterval.to,
        CandleResolution._1MIN
      )
    ).map(o => Option(o.orElse(null)))
      .map(_.toSeq.flatMap(convert))
    val intervals = split[OffsetDateTime, Long](
      interval = interval,
      range = 1.day.toMinutes,
      offset = 1
    )
    intervals.parTraverse(get).map(_.flatten)
//    intervals.traverse(get).map(_.flatten)
  }

  private def makeStream(
      instrumentId: InstrumentId
  )(maybeCandles: Option[Candles]): F[Stream[F, Point]] =
    maybeCandles match {
      case Some(value) =>
        val candles = value.getCandles.asScala.map { candle =>
          Point(
            candle.getTime,
            candle.getO.doubleValue.taggedWith[Tags.Price]
          ) //todo check time <-> o/c price
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
