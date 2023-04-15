package com.github.ppotseluev.algorate.trader.policy

import cats.implicits._
import com.github.ppotseluev.algorate.Currency
import com.github.ppotseluev.algorate.Money
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest

class MoneyManagementPolicy(money: () => Option[Money])(
    maxPercentage: Double,
    maxAbsolute: Map[Currency, Double],
    allowFractionalLots: Boolean = true
) extends Policy {
  override def apply(request: TradeRequest): Decision = {
    val availableMoney = money().orEmpty.getOrElse(request.currency, BigDecimal(0))
    val moneyForTrade = math.min(
      maxAbsolute.getOrElse(request.currency, 0d),
      (maxPercentage * availableMoney).toDouble
    )
    val lots: Double = {
      val x = moneyForTrade / request.price
      if (allowFractionalLots) x.toDouble
      else x.toInt
    }
    if (lots > 0) Decision.Allowed(lots)
    else Decision.Denied(s"Not enough money ($availableMoney) for trade $request")
  }
}
