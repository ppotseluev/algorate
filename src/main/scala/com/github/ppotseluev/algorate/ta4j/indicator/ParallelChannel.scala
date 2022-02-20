package com.github.ppotseluev.algorate.ta4j.indicator

import com.github.ppotseluev.algorate.ta4j.indicator.ChannelIndicator.Channel
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.CachedIndicator

class ParallelChannel(
    channelIndicator: Indicator[Option[Channel]],
    maxDelta: Double
) extends CachedIndicator[Option[Channel]](channelIndicator) {
  override def calculate(index: Int): Option[Channel] =
    channelIndicator.getValue(index).filter { channel =>
      (channel.lowerBoundApproximation.func, channel.uppperBoundApproximation.func) match {
        case (p1: PolynomialFunction, p2: PolynomialFunction) =>
          (p1.getCoefficients, p2.getCoefficients) match {
            case (Array(_, k1), Array(_, k2)) =>
              math.abs((k1 - k2) / math.max(k1, k2)) < maxDelta
            case _ => false
          }
        case _ => false
      }
    }
}
