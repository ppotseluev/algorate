package com.github.ppotseluev.algorate.model

import enumeratum._
import scala.concurrent.duration._
//todo remove?
sealed trait CandleResolution extends EnumEntry {
  def value: Duration
}

object CandleResolution extends Enum[CandleResolution] {
  case object OneMin extends CandleResolution {
    override val value: Duration = 1.minute
  }

  override val values: IndexedSeq[CandleResolution] = findValues
}
