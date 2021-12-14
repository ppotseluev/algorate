package com.github.ppotseluev.algorate.model

import enumeratum._

case class Order(
    instrumentId: InstrumentId,
    lots: Int,
    operationType: OperationType,
    details: Order.Details,
    info: Order.Info
) {
  def isClosing: Boolean = info.closingOrderType.isDefined

  def estimatedCost: Double = details.estimatedPrice * lots

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

    def estimatedPrice: Price
  }

  object Details {
    case class Limit(price: Price) extends Order.Details {
      override def `type`: Type = Order.Type.Limit

      override def estimatedPrice: Price = price
    }

    case class Market(estimatedPrice: Price) extends Order.Details {
      override def `type`: Type = Order.Type.Market
    }
  }

  case class Info(
      closingOrderType: Option[ClosePositionOrder.Type]
  )
}
