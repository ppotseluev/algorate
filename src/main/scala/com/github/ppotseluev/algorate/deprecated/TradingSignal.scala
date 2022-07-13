package com.github.ppotseluev.algorate.deprecated

import cats.data.NonEmptyList
import com.github.ppotseluev.algorate.deprecated.TradingSignal.Decision
import com.github.ppotseluev.algorate.model.{OperationType, Point, Price}

trait TradingSignal {
  def push(newPoints: NonEmptyList[Point]): Unit

  final def push(point: Point): Unit = push(NonEmptyList.one(point))

  def apply(actualPoint: Point): Decision
}

object TradingSignal {
  sealed trait Decision {
    def rawSignal: Double
  }

  object Decision {

    /**
     * @param confidence number from [0; 1]
     */
    case class Trade(
        operationType: OperationType,
        confidence: Double,
        takeProfit: Price,
        stopLoss: Price,
        rawSignal: Double
    ) extends Decision
  }

}
