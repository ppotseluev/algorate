package com.github.ppotseluev.algorate.broker.tinkoff

import cats.Functor
import cats.effect.Sync
import cats.effect.kernel.Concurrent
import com.github.ppotseluev.algorate.{Bar, Order, OrderId, TradingAsset}
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.{CandlesInterval, OrderPlacementInfo}
import io.github.paoloboni.binance.spot.{SpotApi, SpotTimeInForce}
import cats.implicits._
import io.github.paoloboni.binance.common.Interval
import io.github.paoloboni.binance.spot.parameters.v3.KLines
import io.github.paoloboni.binance.spot.parameters.{SpotOrderCreateParams, SpotOrderQueryParams}
import io.github.paoloboni.binance.spot.response.{ExchangeInformation, SpotAccountInfoResponse}

import java.math.MathContext
import scala.math.BigDecimal.RoundingMode

class BinanceBroker[F[_]: Concurrent](binanceClient: SpotApi[F]) extends Broker[F] {
  override def getBalance: F[Any] = {
    binanceClient.V3.getBalance().widen
  }

  def getExchangeInfo: ExchangeInformation =
    binanceClient.exchangeInfo

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    binanceClient.V3
      .queryOrder(
        SpotOrderQueryParams(
          symbol = "STUB", //TODO
          orderId = orderId.toLong.some
        )
      )
      .map { resp =>
        OrderPlacementInfo(
          orderId = orderId,
          status = BinanceConverters.convert(resp.status)
        )
      }

  override def placeOrder(order: Order): F[OrderPlacementInfo] = {
    val stop = SpotOrderCreateParams.STOP_LOSS_LIMIT(
      symbol = order.instrumentId,
      side = BinanceConverters.convert(order.operationType.reverse),
      timeInForce = SpotTimeInForce.FOK, //TODO
      quantity = order.lots,
      price = order.exitBounds.stopLoss, //TODO
      stopPrice = order.exitBounds.stopLoss,
      icebergQty = None
    )
    val take = SpotOrderCreateParams.TAKE_PROFIT_LIMIT(
      symbol = order.instrumentId,
      side = BinanceConverters.convert(order.operationType.reverse),
      timeInForce = SpotTimeInForce.FOK, //TODO
      quantity = order.lots,
      price = order.exitBounds.takeProfit, //TODO
      stopPrice = order.exitBounds.takeProfit,
      icebergQty = None
    )
    val params = SpotOrderCreateParams.MARKET(
      symbol = order.instrumentId,
      side = BinanceConverters.convert(order.operationType),
      quantity = order.lots.some,
      newClientOrderId = order.key.some
    )
    binanceClient.V3.createOrder(params).map {
      resp => //TODO return real executed quantity to exit position correctly?
        OrderPlacementInfo(
          orderId = resp.orderId.toString,
          status = BinanceConverters.convert(resp.status)
        )
    } <* List(stop, take).traverse(binanceClient.V3.createOrder)
  }

  override def getData(asset: TradingAsset, interval: CandlesInterval): F[List[Bar]] =
    binanceClient.V3
      .getKLines(
        KLines(
          symbol = asset.instrumentId,
          interval = BinanceConverters.convert(interval.resolution),
          startTime = interval.interval.firstDay.start.some,
          endTime = interval.interval.lastDay.end.some,
          limit = Int.MaxValue
        )
      )
      .map(BinanceConverters.convert(interval.resolution.duration))
      .compile
      .toList

}
