package com.github.ppotseluev.algorate.strategy.ta4j.indicator

import com.github.ppotseluev.algorate.strategy.ta4j.indicator.ChannelIndicator.Channel
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction

object ChannelUtils {

  def isParallel(maxDelta: Double)(channel: Channel): Boolean =
    (channel.lowerBoundApproximation.func, channel.upperBoundApproximation.func) match {
      case (p1: PolynomialFunction, p2: PolynomialFunction) =>
        (p1.getCoefficients, p2.getCoefficients) match {
          case (Array(_, k1), Array(_, k2)) =>
            math.abs((k1 - k2) / math.max(k1, k2)) <= maxDelta
          case _ => false
        }
      case _ => false
    }

  def isWide(minPercent: Double)(channel: Channel): Boolean =
    (channel.lowerBoundApproximation.func, channel.upperBoundApproximation.func) match {
      case (p1: PolynomialFunction, p2: PolynomialFunction) =>
        (p1.getCoefficients, p2.getCoefficients) match {
          case (Array(a1, _), Array(a2, _)) =>
            math.abs((a1 - a2) / math.min(a1, a2)) >= minPercent
          case _ => false
        }
      case _ => false
    }
}
