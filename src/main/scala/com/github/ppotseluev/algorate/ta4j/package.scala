package com.github.ppotseluev.algorate

import cats.Functor
import org.ta4j.core.Rule
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.rules.AndRule

package object ta4j {

  implicit val IndicatorFunctor: Functor[AbstractIndicator] = new Functor[AbstractIndicator] {
    override def map[A, B](fa: AbstractIndicator[A])(f: A => B): AbstractIndicator[B] =
      new AbstractIndicator[B](fa.getBarSeries) {
        override def getValue(index: Int): B = f(fa.getValue(index))
      }
  }

  implicit class RuleSyntax(val rule: Rule) extends AnyVal {
    def &(other: Rule): Rule =
      new AndRule(rule, other)
  }
}
