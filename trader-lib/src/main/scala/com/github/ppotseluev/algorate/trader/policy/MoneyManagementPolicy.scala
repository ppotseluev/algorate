package com.github.ppotseluev.algorate.trader.policy

import cats.implicits._
import com.github.ppotseluev.algorate.Currency
import com.github.ppotseluev.algorate.broker.MoneyTracker
import com.github.ppotseluev.algorate.trader.policy.Policy.{Decision, TradeRequest}

class MoneyManagementPolicy[F[_]](moneyTracker: MoneyTracker[F])(
    maxPercentage: Double,
    maxAbsolute: Map[Currency, Double]
) extends Policy {
  override def apply(request: TradeRequest): Decision = {
    val availableMoney = moneyTracker.get.orEmpty.getOrElse(request.currency, BigDecimal(0))
    val moneyForTrade = math.min(
      maxAbsolute.getOrElse(request.currency, 0d),
      (maxPercentage * availableMoney).toDouble
    )
    val lots = (moneyForTrade / request.price).toInt
    if (lots > 0) Decision.Allowed(lots)
    else Decision.Denied(s"Not enough money ($availableMoney) for trade $request")
  }
}
