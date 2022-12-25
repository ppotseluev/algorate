package com.github.ppotseluev.algorate.strategy

import org.ta4j.core.Rule
import org.ta4j.core.rules.AndRule

package object ta4j {

  implicit class RuleSyntax(val rule: Rule) extends AnyVal {
    def &(other: Rule): Rule =
      new AndRule(rule, other)
  }
}
