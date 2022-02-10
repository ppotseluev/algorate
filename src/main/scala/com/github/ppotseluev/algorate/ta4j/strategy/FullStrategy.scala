package com.github.ppotseluev.algorate.ta4j.strategy

import org.ta4j.core.num.Num
import org.ta4j.core.{Indicator, Strategy}

case class FullStrategy(
    longStrategy: Strategy,
    shortStrategy: Strategy,
    priceIndicators: Map[String, Indicator[Num]],
    oscillators: Map[String, Indicator[Num]]
)
