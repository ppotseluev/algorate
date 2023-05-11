package com.github.ppotseluev.algorate

import org.ta4j.core.BarSeries
import org.ta4j.core.Rule
import org.ta4j.core.indicators.AbstractIndicator
import org.ta4j.core.rules.AndRule
import org.ta4j.core.rules.BooleanRule

package object strategy {
  type StrategyBuilder = AssetData => FullStrategy

  implicit class RuleSyntax(val rule: Rule) extends AnyVal {
    def &(other: Rule): Rule =
      new AndRule(rule, other)

    def useIf(p: Boolean): Option[Rule] =
      Option.when(p)(rule)

    def featureRuleOrTrue(implicit feature: StrategyFeature): Rule = feature match {
      case StrategyFeature.Disabled => BooleanRule.TRUE
      case StrategyFeature.Enabled  => rule
    }
  }

  implicit class OptionRuleSyntax(val rule: Option[Rule]) extends AnyVal {
    def orTrue: Rule = rule.getOrElse(BooleanRule.TRUE)
    def orFalse: Rule = rule.getOrElse(BooleanRule.FALSE)
  }

  private abstract class Ind[T](implicit barSeries: BarSeries)
      extends AbstractIndicator[T](barSeries)

  def ind[T](f: Int => T)(implicit barSeries: BarSeries): AbstractIndicator[T] = new Ind[T] {
    override def getValue(index: Int): T = f(index)
  }
}
