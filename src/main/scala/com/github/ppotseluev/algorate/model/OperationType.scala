package com.github.ppotseluev.algorate.model

import enumeratum._

sealed trait OperationType extends EnumEntry

object OperationType extends Enum[OperationType] {
  case object Buy extends OperationType
  case object Sell extends OperationType

  override val values: IndexedSeq[OperationType] = findValues
}
