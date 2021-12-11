package com.github.ppotseluev.algorate.model

import enumeratum._

sealed trait OperationType extends EnumEntry {
  def reverse: OperationType = this match {
    case OperationType.Buy => OperationType.Sell
    case OperationType.Sell => OperationType.Buy
  }
}

object OperationType extends Enum[OperationType] {
  case object Buy extends OperationType
  case object Sell extends OperationType

  override val values: IndexedSeq[OperationType] = findValues
}
