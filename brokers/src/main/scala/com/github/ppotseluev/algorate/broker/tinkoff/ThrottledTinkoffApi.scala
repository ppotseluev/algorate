package com.github.ppotseluev.algorate.broker.tinkoff

import com.github.ppotseluev.algorate.BrokerAccountId
import com.github.ppotseluev.algorate.OrderId

import java.time.Instant
import ru.tinkoff.piapi.contract.v1._
import ru.tinkoff.piapi.core.models.Positions
import upperbound.Limiter

class ThrottledTinkoffApi[F[_]](
    delegate: TinkoffApi[F],
    candlesLimiter: Limiter[F]
) extends TinkoffApi[F] {
  override def postOrder(
      figi: String,
      quantity: Long,
      price: Quotation,
      direction: OrderDirection,
      accountId: String,
      orderType: OrderType,
      orderId: String
  ): F[PostOrderResponse] =
    //TODO rate limit?
    delegate.postOrder(figi, quantity, price, direction, accountId, orderType, orderId)

  override def getCandles(
      figi: String,
      from: Instant,
      to: Instant,
      interval: CandleInterval
  ): F[List[HistoricCandle]] =
    candlesLimiter.submit(delegate.getCandles(figi, from, to, interval))

  override def getAllShares: F[List[Share]] =
    delegate.getAllShares

  override def getOderState(accountId: BrokerAccountId, orderId: OrderId): F[OrderState] =
    delegate.getOderState(accountId, orderId)

  override def getPositions(accountId: BrokerAccountId): F[Positions] =
    delegate.getPositions(accountId)
}
