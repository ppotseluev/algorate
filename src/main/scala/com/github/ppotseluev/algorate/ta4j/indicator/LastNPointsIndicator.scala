//package com.github.ppotseluev.algorate.ta4j.indicator
//
//import org.ta4j.core.Indicator
//import org.ta4j.core.indicators.CachedIndicator
//
//class LastNPointsIndicator[T](indicator: Indicator[Option[T]])
//extends CachedIndicator[Seq[(Int, T)]](indicator) {
//  override def calculate(index: Int): Seq[(Int, T)] =
//    LazyList
//      .from(index, -1)
//      .takeWhile(_ >= 0)
//      .flatMap { idx =>
//        indicator.getValue(idx) match {
//          case Some(tag(value)) => Some(idx -> value)
//          case _                => None
//        }
//      }
//      .take(numOfPoints)
//      .reverse
//      .toList
//}
