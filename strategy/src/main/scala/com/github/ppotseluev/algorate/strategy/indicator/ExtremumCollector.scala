package com.github.ppotseluev.algorate.strategy.indicator

import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator.Extremum
import scala.annotation.tailrec

object ExtremumCollector {

  type Merge[T] = (T, T) => Option[T]
  def noMerge[T]: Merge[T] = (_, _) => None

  def mergeCollectReverse[T <: Extremum: Merge](source: LazyList[T], count: Int): List[T] =
    loop(Nil, source, count)

  @tailrec
  private def loop[T: Merge](acc: List[T], source: LazyList[T], targetCount: Int): List[T] =
    if (acc.size == targetCount) {
      acc
    } else {
      val needMore = targetCount - acc.size
      val (extremums, rest) = source.splitAt(needMore)
      extremums.toList match {
        case head :: next =>
          val merged = merge(head, next, acc)
          loop(merged, rest, targetCount)
        case Nil => acc
      }
    }

  @tailrec
  private def merge[T](x: T, xs: List[T], acc: List[T])(implicit merger: Merge[T]): List[T] = {
    val newAcc = acc match {
      case headAcc :: tailAcc =>
        merger.apply(headAcc, x) match {
          case Some(merged) => merged :: tailAcc
          case None         => x :: headAcc :: tailAcc
        }
      case Nil => x :: Nil
    }
    xs match {
      case y :: ys => merge(y, ys, newAcc)
      case Nil     => newAcc
    }
  }
}
