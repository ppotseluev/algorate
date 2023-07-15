package com.github.ppotseluev.algorate

import enumeratum._
//
//case class ClosePositionOrder(
//    originalOrder: Order,
//    targetPrice: Price,
//    `type`: Type
//) {
//  def isFired(currentPrice: Price): Boolean = (`type`, originalOrder.operationType) match {
//    case (Type.TakeProfit, OperationType.Buy) | (Type.StopLoss, OperationType.Sell) =>
//      currentPrice >= targetPrice
//    case (Type.TakeProfit, OperationType.Sell) | (Type.StopLoss, OperationType.Buy) =>
//      currentPrice <= targetPrice
//  }
//
//  def buildMarketOrder(currentPoint: Point): Order =
//    Order(
//      asset = originalOrder.asset,
//      lots = originalOrder.lots,
//      operationType = originalOrder.operationType.reverse,
//      details = Order.Details.Market,
//      info = Info(currentPoint, Some(`type`))
//    )
//}

object ClosePositionOrder {

  sealed trait StopType extends EnumEntry

  object StopType extends Enum[StopType] {
    case object StopLoss extends StopType
    case object TakeProfit extends StopType

    override val values: IndexedSeq[StopType] = findValues
  }
}
