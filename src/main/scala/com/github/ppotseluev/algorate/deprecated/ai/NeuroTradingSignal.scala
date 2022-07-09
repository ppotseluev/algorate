package com.github.ppotseluev.algorate.deprecated.ai

import cats.data.NonEmptyList
import com.github.ppotseluev.algorate.core.Point
import com.github.ppotseluev.algorate.core.TradingSignal
import com.github.ppotseluev.algorate.core.TradingSignal.Decision
import com.github.ppotseluev.algorate.model.OperationType
import com.github.ppotseluev.algorate.model.Price
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.eann.neural.Net
import com.softwaremill.tagging.Tagger

class NeuroTradingSignal(
    net: Net,
//    operationType: OperationType,
    normalizer: Price => Double,
    takeProfitPercent: Double,
    stopLossPercent: Double,
    keepPointsCount: Int = 10
) extends TradingSignal {
//  private val buffer: mutable.Queue[Point] = new LimitedQueue(keepPointsCount)
  @volatile private var cached: Option[(Point, Decision)] = None

  override def push(newPoints: NonEmptyList[Point]): Unit = {
    val decisions = newPoints.map(point => point -> apply(point))
    cached = Some(decisions.last)
    ()
//    buffer ++= newPoints.toList
  }

  override def apply(actualPoint: Point): Decision = //{
    cached match {
      case Some((`actualPoint`, decision)) => decision
      case _ =>
        val input = Array(normalizer.apply(actualPoint.value))
        val normalizedOut = {
          //      if (buffer.length >= keepPointsCount) {
          //        val input = buffer.toArray.map(_.value).map(normalizer)
          val Array(output) = net.calculate(input)
          2 * (output - 0.5) //scale to [-1; 1]
          //      } else {
          //        0
          //      }
        }
        val operationType = if (normalizedOut >= 0) OperationType.Buy else OperationType.Sell
        val (takeProfit, stopLoss) = operationType match {
          case OperationType.Buy =>
            (
              actualPoint.value * (1 + takeProfitPercent / 100),
              actualPoint.value * (1 - stopLossPercent / 100)
            )
          case OperationType.Sell =>
            (
              actualPoint.value * (1 - takeProfitPercent / 100),
              actualPoint.value * (1 + stopLossPercent / 100)
            )
        }
        val decision = Decision.Trade(
          operationType = operationType,
          confidence = math.abs(normalizedOut),
          takeProfit = takeProfit.taggedWith[Tags.Price],
          stopLoss = stopLoss.taggedWith[Tags.Price],
          rawSignal = normalizedOut
        )
//        cached = Some(actualPoint -> decision)
        decision
    }
}
