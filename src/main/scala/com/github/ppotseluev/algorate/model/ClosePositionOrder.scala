package com.github.ppotseluev.algorate.model

import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
import enumeratum.{Enum, EnumEntry}

case class ClosePositionOrder(
    originalOrder: Order,
    targetPrice: Price,
    `type`: Type
) {
  def isFired(currentPrice: Price): Boolean = (`type`, originalOrder.operationType) match {
    case (Type.TakeProfit, OperationType.Buy) | (Type.StopLoss, OperationType.Sell) =>
      currentPrice >= targetPrice
    case (Type.TakeProfit, OperationType.Sell) | (Type.StopLoss, OperationType.Buy) =>
      currentPrice <= targetPrice
  }

  def buildMarketOrder: Order = {
    val operationType = originalOrder.operationType match {
      case OperationType.Buy  => OperationType.Sell
      case OperationType.Sell => OperationType.Buy
    }
    Order(
      instrumentId = originalOrder.instrumentId,
      lots = originalOrder.lots,
      operationType = operationType,
      details = Order.Details.Market
    )
  }
}

object ClosePositionOrder {

  sealed trait Type extends EnumEntry

  object Type extends Enum[Type] {
    case object StopLoss extends Type
    case object TakeProfit extends Type

    override val values: IndexedSeq[Type] = findValues
  }
}
