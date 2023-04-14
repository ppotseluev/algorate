package com.github.ppotseluev.algorate

import org.ta4j.core.BarSeries
import org.ta4j.core.Rule
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.rules.AndRule

package object strategy {

  implicit class RuleSyntax(val rule: Rule) extends AnyVal {
    def &(other: Rule): Rule =
      new AndRule(rule, other)
  }

  private abstract class Ind[T](implicit barSeries: BarSeries)
      extends AbstractIndicator[T](barSeries)

  def ind[T](f: Int => T)(implicit barSeries: BarSeries): AbstractIndicator[T] = new Ind[T] {
    override def getValue(index: Int): T = f(index)
  }
}
