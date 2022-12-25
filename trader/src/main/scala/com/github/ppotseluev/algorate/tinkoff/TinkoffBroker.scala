package com.github.ppotseluev.algorate.tinkoff

import cats.Parallel
import cats.effect.kernel.Async
import cats.syntax.functor._
import cats.syntax.parallel._
import com.github.ppotseluev.algorate.broker.Broker
import Broker.CandleResolution
import Broker.CandlesInterval
import Broker.Day
import Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.model
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.Order.Type
import com.github.ppotseluev.algorate.model._
import com.github.ppotseluev.algorate.proto.ProtoConverters
import com.github.ppotseluev.algorate.math.RealNumber

import java.time.ZoneId
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderState
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

  override def getOrderState(orderId: OrderId): F[OrderState] =
    api.getOderState(brokerAccountId, orderId)

  override def placeOrder(order: Order): F[OrderPlacementInfo] =
    api
      .postOrder(
        order.instrumentId,
        order.lots,
        price(order),
        orderDirection(order),
        brokerAccountId,
        orderType(order),
        order.key
      )
      .map { r =>
        OrderPlacementInfo(r.getOrderId, r.getExecutionReportStatus)
      }

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
