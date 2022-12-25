package com.github.ppotseluev.algorate.tinkoff

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.syntax.functor._
import com.github.ppotseluev.algorate.model.BrokerAccountId
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.util.fromJavaFuture
import java.time.Instant
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.PostOrderResponse
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
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
  def wrap[F[_]: Async](investApi: InvestApi): TinkoffApi[F] = new TinkoffApi[F] {
    override def postOrder(
        figi: String,
        quantity: Long,
        price: Quotation,
        direction: OrderDirection,
        accountId: String,
        orderType: OrderType,
        orderId: String
    ): F[PostOrderResponse] = fromJavaFuture {
      investApi.getOrdersService.postOrder(
        figi,
        quantity,
        price,
        direction,
        accountId,
        orderType,
        orderId
      )
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
