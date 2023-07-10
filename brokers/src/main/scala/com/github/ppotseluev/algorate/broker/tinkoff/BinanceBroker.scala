package com.github.ppotseluev.algorate.broker.tinkoff

import cats.{Functor, Parallel}
import cats.effect.{Async, Sync}
import cats.effect.kernel.Concurrent
import com.github.ppotseluev.algorate.{Bar, OperationType, Order, OrderId, Ticker, TradingAsset}
import com.github.ppotseluev.algorate.broker.{
  Archive,
  ArchiveCachedBroker,
  Broker,
  RedisCachedBroker
}
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
import com.typesafe.scalalogging.LazyLogging
import dev.profunktor.redis4cats.RedisCommands
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
import java.nio.file.Path
import java.util.{List => JList}
import scala.concurrent.Promise
import scala.concurrent.Promise
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Success}

class BinanceBroker[F[_]: Concurrent: Async](
    spotApi: SpotApi[F],
    spotClient: BinanceApiAsyncRestClient,
    marginClient: BinanceApiAsyncMarginRestClient,
    fee: BigDecimal = 0.001 //0.1%
) extends Broker[F]
    with LazyLogging {
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
      val quantityF =
        if (order.isClosing) { //closing short position
          getMarginAccount.map { acc =>
            val balance = acc.getAssetBalance(order.asset.symbol)
            val netAsset = BigDecimal(balance.getNetAsset)
            val toRepay = if (netAsset < 0) netAsset.abs else BigDecimal(0)
            if (toRepay == 0) {
              logger.warn(s"Repay amount is zero, requested order $order")
            }
            //TODO use BNB balance to pay fee & interest rate?
            val toBuy = (toRepay / (1 - fee)).setScale(order.asset.quantityScale, RoundingMode.UP)
            toBuy -> toRepay.some
          }
        } else {
          (order.lots -> none).pure[F]
        }
      val makeOrder = order.operationType match {
        case OperationType.Buy  => MarginNewOrder.marketBuy _
        case OperationType.Sell => MarginNewOrder.marketSell _
      }
      val borrow = invoke(marginClient.borrow(order.asset.symbol, order.lots.toString, _))
      def repay(amount: BigDecimal) = invoke(
        marginClient.repay(order.asset.symbol, amount.toString, _)
      )
      for {
        quantity <- quantityF
        _ <- borrow.unlessA(order.isClosing)
        marginOrder = makeOrder(order.instrumentId, quantity._1.toString)
        //TODO 'repay full' like in app instead order & repay?
        resp <- invoke(marginClient.newOrder(marginOrder, _)).map { resp =>
          OrderPlacementInfo(
            orderId = resp.getOrderId.toString,
            status = BinanceConverters.convert(resp.getStatus)
          )
        }
        _ <- quantity._2.fold(().pure[F])(repay(_).void)
      } yield resp
    } else {
      val quantityF =
        if (order.isClosing) { //closing long position
          getBalance(nonZero = false).map { b =>
            val availableBalance = b.balances
              .find(_.asset == order.asset.symbol)
              .map(_.free)
              .getOrElse(
                throw new NoSuchElementException(s"Not found balance for ${order.asset.symbol}")
              )
            availableBalance
              .min(order.lots)
              .setScale(order.asset.quantityScale, RoundingMode.DOWN)
          }
        } else { //enter long position
          order.lots.pure[F]
        }
      for {
        quantity <- quantityF
        params = SpotOrderCreateParams.MARKET(
          symbol = order.instrumentId,
          side = BinanceConverters.convert(order.operationType),
          quantity = quantity.some,
          newClientOrderId = order.key.some
        )
        resp <- spotApi.V3.createOrder(params).map { resp =>
          OrderPlacementInfo(
            orderId = resp.orderId.toString,
            status = BinanceConverters.convert(resp.status)
          )
        } // <* List(stop, take).traverse(spotApi.V3.createOrder)
      } yield resp
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

object BinanceBroker {
  def cached[F[_]: Concurrent: Async: Parallel](
      spotApi: SpotApi[F],
      spotClient: BinanceApiAsyncRestClient,
      marginClient: BinanceApiAsyncMarginRestClient,
      barsCache: Either[(String, Path), RedisCommands[F, String, List[Bar]]],
      fee: BigDecimal = 0.001 //0.1
  ): BinanceBroker[F] = {
    val binanceBroker = new BinanceBroker[F](spotApi, spotClient, marginClient, fee)
    new BinanceBroker[F](spotApi, spotClient, marginClient, fee) {
      private val cachedBroker = barsCache match {
        case Left(token -> archiveDir) =>
          val archive = new Archive[F](token, archiveDir)
          new ArchiveCachedBroker(binanceBroker, archive)
        case Right(redisCache) =>
          new RedisCachedBroker(binanceBroker, redisCache)
      }

      override def getData(asset: TradingAsset, interval: CandlesInterval): F[List[Bar]] =
        cachedBroker.getData(asset, interval)
    }
  }

}
