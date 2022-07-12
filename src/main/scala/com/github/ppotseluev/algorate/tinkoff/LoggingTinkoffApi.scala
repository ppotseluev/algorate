package com.github.ppotseluev.algorate.tinkoff
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import ru.tinkoff.piapi.contract.v1._

import java.time.Instant

class LoggingTinkoffApi[F[_]: Sync](api: TinkoffApi[F]) extends TinkoffApi[F] with StrictLogging {
  override def postOrder(
      figi: String,
      quantity: Long,
      price: Quotation,
      direction: OrderDirection,
      accountId: String,
      orderType: OrderType,
      orderId: String
  ): F[PostOrderResponse] =
    api.postOrder(figi, quantity, price, direction, accountId, orderType, orderId)

  override def getCandles(
      figi: String,
      from: Instant,
      to: Instant,
      interval: CandleInterval
  ): F[List[HistoricCandle]] =
    api.getCandles(figi, from, to, interval).onError { case e =>
      Sync[F].delay(logger.error(s"Failed getCandles $figi for from $from to $to", e))
    }

  override def getAllShares: F[List[Share]] = api.getAllShares
}