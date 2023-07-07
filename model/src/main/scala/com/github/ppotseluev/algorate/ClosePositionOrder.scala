package com.github.ppotseluev.algorate

import com.github.ppotseluev.algorate.ClosePositionOrder.Type
import com.github.ppotseluev.algorate.Order.Info
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

  sealed trait Type extends EnumEntry

  object Type extends Enum[Type] {
    case object StopLoss extends Type
    case object TakeProfit extends Type

    override val values: IndexedSeq[Type] = findValues
  }
}
