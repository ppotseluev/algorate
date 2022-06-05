package com.github.ppotseluev.algorate.model

import com.github.ppotseluev.algorate.core.Point
import com.github.ppotseluev.algorate.model.Order.Details
import enumeratum._

case class Order(
    instrumentId: InstrumentId,
    lots: Int,
    operationType: OperationType,
    details: Order.Details,
    info: Order.Info
) {
  def isClosing: Boolean = info.closingOrderType.isDefined

  def price: Price = details match {
    case Details.Limit(orderPrice) => orderPrice
    case Details.Market            => info.point.value
  }

  def estimatedCost: BigDecimal = price * lots

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
