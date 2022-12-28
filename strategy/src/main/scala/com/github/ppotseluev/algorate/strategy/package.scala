package com.github.ppotseluev.algorate

import org.ta4j.core.Rule
import org.ta4j.core.rules.AndRule

package object strategy {

  implicit class RuleSyntax(val rule: Rule) extends AnyVal {
    def &(other: Rule): Rule =
      new AndRule(rule, other)
  }
}
