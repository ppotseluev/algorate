package com.github.ppotseluev.algorate

import com.github.ppotseluev.algorate.Order.Details
import enumeratum._

case class Order(
    asset: TradingAsset,
    lots: Double,
    operationType: OperationType,
    details: Order.Details,
    info: Order.Info
) {
  def instrumentId: InstrumentId = asset.instrumentId

  def key: String = {
    val ts = info.point.timestamp.toInstant.getEpochSecond
    s"$instrumentId-$ts"
  }

  def isClosing: Boolean = info.closingOrderType.isDefined

  def price: Price = details match {
    case Details.Limit(orderPrice) => orderPrice
    case Details.Market            => info.point.value
  }

  def estimatedCost: BigDecimal = price * lots

  /**
   * Order that closes this order at the specified point
   */
  def buildClosingOrder(point: Point): Order = {
    val isProfitable = operationType match {
      case OperationType.Buy  => point.value > this.price
      case OperationType.Sell => point.value < this.price
    }
    val closingOrderType =
      if (isProfitable) ClosePositionOrder.Type.TakeProfit
      else ClosePositionOrder.Type.StopLoss
    this.copy(
      operationType = operationType.reverse,
      details = Order.Details.Market,
      info = Order.Info(
        point = point,
        closingOrderType = Some(closingOrderType)
      )
    )
  }

  require(lots > 0, "lots must be positive")
}

object Order {
  sealed trait Type extends EnumEntry

  object Type extends Enum[Type] {
    case object Limit extends Order.Type
    case object Market extends Order.Type

    override val values: IndexedSeq[Type] = findValues
  }

  sealed trait Details {
    def `type`: Order.Type
  }

  object Details {
    case class Limit(price: Price) extends Order.Details {
      override def `type`: Type = Order.Type.Limit
    }

    case object Market extends Order.Details {
      override def `type`: Type = Order.Type.Market
    }
  }

  case class Info(
      point: Point,
      closingOrderType: Option[ClosePositionOrder.Type]
  )
}
