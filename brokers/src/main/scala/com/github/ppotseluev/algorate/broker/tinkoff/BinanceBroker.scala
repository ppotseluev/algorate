package com.github.ppotseluev.algorate.broker.tinkoff

import cats.Functor
import cats.effect.{Async, Sync}
import cats.effect.kernel.Concurrent
import com.github.ppotseluev.algorate.{Bar, Order, OrderId, Ticker, TradingAsset}
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.{CandlesInterval, OrderPlacementInfo}
import io.github.paoloboni.binance.spot.{SpotApi, SpotTimeInForce}
import cats.implicits._
import com.binance.api.client.domain.account.request.{AllOrdersRequest, OrderRequest}
import com.binance.api.client.domain.account.{Order => BinanceOrder}
import com.binance.api.client.{BinanceApiAsyncRestClient, BinanceApiCallback, BinanceApiRestClient}
import io.github.paoloboni.binance.common.Interval
import io.github.paoloboni.binance.spot.parameters.v3.KLines
import io.github.paoloboni.binance.spot.parameters.{SpotOrderCreateParams, SpotOrderQueryParams}
import io.github.paoloboni.binance.spot.response.{ExchangeInformation, SpotAccountInfoResponse}

import scala.jdk.CollectionConverters._
import java.math.MathContext
import java.util.{List => JList}
import scala.concurrent.Promise
import scala.concurrent.Promise
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success}

class BinanceBroker[F[_]: Concurrent: Async](
    spotApi: SpotApi[F],
    binanceClient: BinanceApiAsyncRestClient
) extends Broker[F] {
  def getBalance: F[SpotAccountInfoResponse] =
    spotApi.V3.getBalance()

  def getExchangeInfo: ExchangeInformation =
    spotApi.exchangeInfo

  private def queryOrders(f: BinanceApiCallback[JList[BinanceOrder]] => Unit) = Async[F]
    .fromFuture {
      Sync[F].delay {
        val promise = Promise[JList[BinanceOrder]]()
        val callback = new BinanceApiCallback[JList[BinanceOrder]] {
          override def onResponse(response: JList[BinanceOrder]): Unit =
            promise.complete(Success(response))

          override def onFailure(cause: Throwable): Unit =
            promise.complete(Failure(cause))
        }
        f(callback)
        promise.future
      }
    }
    .map(_.asScala.toList)

  def getOrders(ticker: Ticker, onlyOpen: Boolean): F[List[BinanceOrder]] =
    if (onlyOpen) {
      val request = new OrderRequest(ticker)
      queryOrders(binanceClient.getOpenOrders(request, _))
    } else {
      val request = new AllOrdersRequest(ticker)
      queryOrders(binanceClient.getAllOrders(request, _))
    }

  override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
    spotApi.V3
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
//    val stop = SpotOrderCreateParams.STOP_LOSS_LIMIT(
//      symbol = order.instrumentId,
//      side = BinanceConverters.convert(order.operationType.reverse),
//      timeInForce = SpotTimeInForce.FOK, //TODO
//      quantity = order.lots,
//      price = order.exitBounds.stopLoss, //TODO
//      stopPrice = order.exitBounds.stopLoss,
//      icebergQty = None
//    )
//    val take = SpotOrderCreateParams.TAKE_PROFIT_LIMIT(
//      symbol = order.instrumentId,
//      side = BinanceConverters.convert(order.operationType.reverse),
//      timeInForce = SpotTimeInForce.FOK, //TODO
//      quantity = order.lots,
//      price = order.exitBounds.takeProfit, //TODO
//      stopPrice = order.exitBounds.takeProfit,
//      icebergQty = None
//    )
    val params = SpotOrderCreateParams.MARKET(
      symbol = order.instrumentId,
      side = BinanceConverters.convert(order.operationType),
      quantity = order.lots.some,
      newClientOrderId = order.key.some
    )
    spotApi.V3.createOrder(params).map {
      resp => //TODO return real executed quantity to exit position correctly?
        OrderPlacementInfo(
          orderId = resp.orderId.toString,
          status = BinanceConverters.convert(resp.status)
        )
    }// <* List(stop, take).traverse(spotApi.V3.createOrder)
  }

  override def getData(asset: TradingAsset, interval: CandlesInterval): F[List[Bar]] =
    spotApi.V3
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
