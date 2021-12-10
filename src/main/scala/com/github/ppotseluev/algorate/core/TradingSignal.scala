package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.core.TradingSignal.Decision
import com.github.ppotseluev.algorate.model.{OperationType, Price}

trait TradingSignal {
  def push(newPoints: Seq[Point]): Unit

  final def push(point: Point): Unit = push(Seq(point))

  def apply(): Decision
}

object TradingSignal {
  sealed trait Decision

  object Decision {

    /**
     * @param confidence number from [0; 1]
     */
    case class Trade(
        operationType: OperationType,
        confidence: Double,
        takeProfit: Price,
        stopLoss: Price
    ) extends Decision

    case object Wait extends Decision
  }

}
