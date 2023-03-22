package com.github.ppotseluev.algorate.trader.policy

import cats.implicits._
import com.github.ppotseluev.algorate.{Currency, Money}
import com.github.ppotseluev.algorate.broker.MoneyTracker
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest

class MoneyManagementPolicy(money: () => Option[Money])(
    maxPercentage: Double,
    maxAbsolute: Map[Currency, Double]
) extends Policy {
  override def apply(request: TradeRequest): Decision = {
    val availableMoney = money().orEmpty.getOrElse(request.currency, BigDecimal(0))
    val moneyForTrade = math.min(
      maxAbsolute.getOrElse(request.currency, 0d),
      (maxPercentage * availableMoney).toDouble
    )
    val lots = (moneyForTrade / request.price).toInt
    if (lots > 0) Decision.Allowed(lots)
    else Decision.Denied(s"Not enough money ($availableMoney) for trade $request")
  }
}
