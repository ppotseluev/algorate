package com.github.ppotseluev.algorate.broker.tinkoff

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.functor._
import com.github.ppotseluev.algorate.BrokerAccountId
import com.github.ppotseluev.algorate.OrderId
import java.time.Instant
import java.util.concurrent.CompletableFuture
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.OrdersServiceGrpc.OrdersServiceStub
import ru.tinkoff.piapi.contract.v1.PostOrderRequest
import ru.tinkoff.piapi.contract.v1.PostOrderResponse
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import ru.tinkoff.piapi.core.utils.Helpers
import scala.jdk.CollectionConverters._
import upperbound.Limiter

/**
 * Tiny scala wrapper over [[ru.tinkoff.piapi.core.InvestApi]]
 */
trait TinkoffApi[F[_]] {

  def postOrder(
      figi: String,
      quantity: Long,
      price: Quotation,
      direction: OrderDirection,
      accountId: String,
      orderType: OrderType,
      orderId: String
  ): F[PostOrderResponse]

  def getCandles(
      figi: String,
      from: Instant,
      to: Instant,
      interval: CandleInterval
  ): F[List[HistoricCandle]]

  def getAllShares: F[List[Share]]

  def getOderState(accountId: BrokerAccountId, orderId: OrderId): F[OrderState]
}

object TinkoffApi {
  private def fromJavaFuture[F[_]: Async, T](future: => CompletableFuture[T]): F[T] =
    Async[F].fromCompletableFuture(Sync[F].delay(future))

  def wrap[F[_]: Async](investApi: InvestApi): TinkoffApi[F] = new TinkoffApi[F] {
    private val ordersStub: OrdersServiceStub = {
      val orderService = investApi.getOrdersService
      val field = orderService.getClass.getDeclaredField("ordersStub")
      field.setAccessible(true)
      try {
        field.get(orderService).asInstanceOf[OrdersServiceStub]
      } finally {
        field.setAccessible(false)
      }
    }

    override def postOrder(
        figi: String,
        quantity: Long,
        price: Quotation,
        direction: OrderDirection,
        accountId: String,
        orderType: OrderType,
        orderId: String
    ): F[PostOrderResponse] = fromJavaFuture {
      val request = PostOrderRequest.newBuilder
        .setInstrumentId(figi)
        .setQuantity(quantity)
        .setPrice(price)
        .setDirection(direction)
        .setAccountId(accountId)
        .setOrderType(orderType)
        .setOrderId(Helpers.preprocessInputOrderId(orderId))
        .setFigi(figi)
        .build
      Helpers.unaryAsyncCall(ordersStub.postOrder(request, _))
    }

    override def getCandles(
        figi: String,
        from: Instant,
        to: Instant,
        interval: CandleInterval
    ): F[List[HistoricCandle]] = fromJavaFuture {
      investApi.getMarketDataService.getCandles(figi, from, to, interval)
    }.map(_.asScala.toList)

    override def getAllShares: F[List[Share]] =
      fromJavaFuture(investApi.getInstrumentsService.getAllShares).map(_.asScala.toList)

    override def getOderState(accountId: BrokerAccountId, orderId: OrderId): F[OrderState] =
      fromJavaFuture {
        investApi.getOrdersService.getOrderState(accountId, orderId)
      }
  }

  implicit class Syntax[F[_]](val api: TinkoffApi[F]) extends AnyVal {
    def withCandlesLimit(limiter: Limiter[F]): TinkoffApi[F] =
      new ThrottledTinkoffApi[F](api, candlesLimiter = limiter)

    def withLogging(implicit F: Sync[F]): TinkoffApi[F] =
      new LoggingTinkoffApi(api)
  }
}
