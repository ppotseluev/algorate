package com.github.ppotseluev.algorate.trader.policy

import cats.implicits._
import com.github.ppotseluev.algorate.Currency
import com.github.ppotseluev.algorate.Money
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingAsset.Type
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest

class MoneyManagementPolicy(money: () => Option[Money])(
    maxPercentage: Double,
    maxAbsolute: Map[Currency, () => Double],
    manualMaxAbsolute: Map[Currency, () => Double],
    allowFractionalLots: TradingAsset.Type => Boolean = MoneyManagementPolicy.fractionalLots
) extends Policy {
  override def apply(request: TradeRequest): Decision = {
    val currency = request.asset.currency
    val availableMoney = money().orEmpty.getOrElse(currency, BigDecimal(0))
    val moneyForTrade = math.min(
      (if (request.manualTrade) manualMaxAbsolute else maxAbsolute)
        .getOrElse(currency, () => 0d)
        .apply(),
      (maxPercentage * availableMoney).toDouble
    )
    val lots: Double = {
      val x = moneyForTrade / request.price
      if (allowFractionalLots(request.asset.`type`)) x.toDouble
      else x.toInt
    }
    if (lots > 0) Decision.Allowed(lots)
    else Decision.Denied(s"Not enough money ($availableMoney) for trade $request")
  }
}

object MoneyManagementPolicy {
  val fractionalLots: TradingAsset.Type => Boolean = {
    case Type.Crypto => true
    case Type.Share  => false
  }
}
