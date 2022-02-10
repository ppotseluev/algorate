package com.github.ppotseluev.algorate

import cats.{Functor, Monoid}
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.rules.AndRule
import org.ta4j.core.utils.BarSeriesUtils
import org.ta4j.core.{BarSeries, BaseBarSeriesBuilder, Rule}

import java.util

package object ta4j {
  //TODO remove
//  implicit val BarSeriesMonoid: Monoid[BarSeries] = new Monoid[BarSeries] {
//    override val empty: BarSeries = new BaseBarSeriesBuilder().build()
//
//    override def combine(x: BarSeries, y: BarSeries): BarSeries = {
//      val name = Option(x.getName).getOrElse(y.getName)
//      val series = new BaseBarSeriesBuilder()
//        .withNumTypeOf(y.numOf(0))
//        .withName(name)
//        .withBars(x.getBarData)
//        .build
//      BarSeriesUtils.addBars(series, new util.ArrayList(y.getBarData))
//      series
//    }
//  }

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
