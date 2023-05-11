package com.github.ppotseluev.algorate.strategy

sealed trait StrategyFeature
object StrategyFeature {
  object Disabled extends StrategyFeature
  object Enabled extends StrategyFeature
}
