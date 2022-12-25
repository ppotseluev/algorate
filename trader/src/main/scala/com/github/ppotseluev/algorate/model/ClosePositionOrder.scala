package com.github.ppotseluev.algorate.model

import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
import com.github.ppotseluev.algorate.model.Order.Info
import enumeratum.Enum
import enumeratum.EnumEntry

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

  def buildMarketOrder(currentPoint: Point): Order =
    Order(
      instrumentId = originalOrder.instrumentId,
      lots = originalOrder.lots,
      operationType = originalOrder.operationType.reverse,
      details = Order.Details.Market,
      info = Info(currentPoint, Some(`type`))
    )
}

object ClosePositionOrder {

  sealed trait Type extends EnumEntry

  object Type extends Enum[Type] {
    case object StopLoss extends Type
    case object TakeProfit extends Type

    override val values: IndexedSeq[Type] = findValues
  }
}
