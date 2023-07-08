package com.github.ppotseluev.algorate.broker.tinkoff

import cats.Functor
import cats.effect.{Async, Sync}
import cats.effect.kernel.Concurrent
import com.github.ppotseluev.algorate.{Bar, OperationType, Order, OrderId, Ticker, TradingAsset}
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.{CandlesInterval, OrderPlacementInfo}
import io.github.paoloboni.binance.spot.{SpotApi, SpotTimeInForce}
import cats.implicits._
import com.binance.api.client.domain.account.request.{AllOrdersRequest, OrderRequest}
import com.binance.api.client.domain.account.{MarginAccount, MarginNewOrder, Order => BinanceOrder}
import com.binance.api.client.{
  BinanceApiAsyncMarginRestClient,
  BinanceApiAsyncRestClient,
  BinanceApiCallback,
  BinanceApiRestClient
}
import io.github.paoloboni.binance.common.Interval
import io.github.paoloboni.binance.spot.parameters.v3.KLines
import io.github.paoloboni.binance.spot.parameters.{
  SpotOrderCancelAllParams,
  SpotOrderCreateParams,
  SpotOrderQueryParams
}
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
    spotClient: BinanceApiAsyncRestClient,
    marginClient: BinanceApiAsyncMarginRestClient
) extends Broker[F] {
  def getBalance(nonZero: Boolean): F[SpotAccountInfoResponse] = {
    spotApi.V3.getBalance().map { resp =>
      val balances =
        if (nonZero) resp.balances.filter(b => b.free != 0 || b.locked != 0)
        else resp.balances
      resp.copy(balances = balances)
    }
  }

  def getMarginAccount: F[MarginAccount] =
    invoke(marginClient.getAccount)

  def getExchangeInfo: ExchangeInformation =
    spotApi.exchangeInfo

  def cancelAllOrders(ticker: Ticker): F[Unit] =
    spotApi.V3.cancelAllOrders(SpotOrderCancelAllParams(ticker))

  private def invoke[T](f: BinanceApiCallback[T] => Unit): F[T] = Async[F]
    .fromFuture {
      Sync[F].delay {
        val promise = Promise[T]()
        val callback = new BinanceApiCallback[T] {
          override def onResponse(response: T): Unit =
            promise.complete(Success(response))

          override def onFailure(cause: Throwable): Unit =
            promise.complete(Failure(cause))
        }
        f(callback)
        promise.future
      }
    }

  def getOrders(ticker: Ticker, onlyOpen: Boolean): F[List[BinanceOrder]] = {
    if (onlyOpen) {
      val request = new OrderRequest(ticker)
      invoke(spotClient.getOpenOrders(request, _))
    } else {
      val request = new AllOrdersRequest(ticker)
      invoke(spotClient.getAllOrders(request, _))
    }
  }.map(_.asScala.toList)

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

  override def placeOrder(order: Order): F[OrderPlacementInfo] =
    if (
      (order.operationType == OperationType.Sell && !order.isClosing) || //enter short
      (order.operationType == OperationType.Buy && order.isClosing) //exit short
    ) {
      val makeOrder = order.operationType match {
        case OperationType.Buy  => MarginNewOrder.marketBuy _
        case OperationType.Sell => MarginNewOrder.marketSell _
      }
      val marginOrder = makeOrder(order.instrumentId, order.lots.toString)
      invoke(marginClient.newOrder(marginOrder, _)).map { resp =>
        OrderPlacementInfo(
          orderId = resp.getOrderId.toString,
          status = BinanceConverters.convert(resp.getStatus)
        )
      }
    } else {
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
      } // <* List(stop, take).traverse(spotApi.V3.createOrder)
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
