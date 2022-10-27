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
import com.github.ppotseluev.algorate.proto.ProtoConverters
import com.github.ppotseluev.algorate.util._
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

  private def convert(candleDuration: FiniteDuration)(candle: HistoricCandle): Bar =
    model.Bar(
      openPrice = TinkoffConverters.price(candle.getOpen),
      closePrice = TinkoffConverters.price(candle.getClose),
      lowPrice = TinkoffConverters.price(candle.getLow),
      highPrice = TinkoffConverters.price(candle.getHigh),
      volume = candle.getVolume,
      endTime = ProtoConverters.fromProto(candle.getTime, zoneId),
      duration = candleDuration
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

  override def getAllShares: F[List[Share]] =
    api.getAllShares
}
