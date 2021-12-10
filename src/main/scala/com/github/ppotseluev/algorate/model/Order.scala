package com.github.ppotseluev.algorate.model

import enumeratum._

case class Order(
    instrumentId: InstrumentId,
    lots: Int,
    operationType: OperationType,
    details: Order.Details
) {
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
    case class Limit(price: Double) extends Order.Details {
      override def `type`: Type = Order.Type.Limit
    }

    case object Market extends Order.Details {
      override def `type`: Type = Order.Type.Market
    }
  }
}
