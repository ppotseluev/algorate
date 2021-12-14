package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.core.TradingSignal.Decision
import com.github.ppotseluev.algorate.model.OperationType.Buy
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.util.LimitedQueue
import com.softwaremill.tagging.Tagger
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

class DummyTradingSignal(keepPointsCount: Int = 5) extends TradingSignal with LazyLogging {

  private val buffer: mutable.Queue[Point] = new LimitedQueue(keepPointsCount)

  override def push(newPoints: Seq[Point]): Unit = {
    buffer ++= newPoints
  }

  override def apply(): Decision = {
//    logger.info(s"Decide based on $buffer")
    val x = math.random()
    if (x > 0.1) {
      Decision.Trade(
        confidence = math.random(),
        takeProfit = (buffer.last.value * 1.0005).taggedWith[Tags.Price],
        stopLoss = (buffer.last.value * 0.7).taggedWith[Tags.Price],
        operationType = Buy
      )
    } else {
      Decision.Wait
    }
  }

}
