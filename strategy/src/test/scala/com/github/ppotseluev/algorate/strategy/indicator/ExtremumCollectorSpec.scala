package com.github.ppotseluev.algorate.strategy.indicator

import cats.implicits._
import com.github.ppotseluev.algorate.strategy.indicator.ExtremumCollector.Merge
import com.github.ppotseluev.algorate.strategy.indicator.ExtremumCollector.noMerge
import com.github.ppotseluev.algorate.strategy.indicator.LocalExtremumIndicator.Extremum
import munit.FunSuite
import org.ta4j.core.num.DoubleNum
import org.ta4j.core.num.Num
import scala.language.implicitConversions

class ExtremumCollectorSpec extends FunSuite {

  implicit def toNum(x: Double): Num = DoubleNum.valueOf(x)

  val extremums = LazyList(
    Extremum.Max(2, 4),
    Extremum.Max(1.02, 3),
    Extremum.Max(1.01, 2),
    Extremum.Max(1.01, 1),
    Extremum.Max(1.01, 1),
    Extremum.Max(1, -3),
    Extremum.Max(1, -10)
  )

  test("return provided data if no merges") {
    implicit val merge: Merge[Extremum.Max] = noMerge
    val result = ExtremumCollector.mergeCollectReverse(extremums, 4)
    val expected = List(
      Extremum.Max(1.01, 1),
      Extremum.Max(1.01, 2),
      Extremum.Max(1.02, 3),
      Extremum.Max(2, 4)
    )
    assertEquals(result, expected)
  }

  test("return all provided data if not enough points") {
    implicit val merge: Merge[Extremum.Max] = noMerge
    val result = ExtremumCollector.mergeCollectReverse(extremums, 100)
    assertEquals(result, extremums.toList.reverse)
  }

  test("work with empty data") {
    implicit val merge: Merge[Extremum.Max] = noMerge
    val result = ExtremumCollector.mergeCollectReverse(LazyList.empty, 100)
    assertEquals(result, Nil)
  }

  test("merge correctly") {
    implicit val merge: Merge[Extremum.Max] = (x, y) =>
      if (math.abs(x.index - y.index) <= 1) {
        List(x, y).maxBy(_.index).some
      } else {
        None
      }
    val result = ExtremumCollector.mergeCollectReverse(extremums, 4)
    val expected = List(
      Extremum.Max(2, 4),
      Extremum.Max(1.01, 2),
      Extremum.Max(1, -3),
      Extremum.Max(1, -10)
    ).reverse
    assertEquals(result, expected)
  }

  test("merge correctly") {
    implicit val merge: Merge[Extremum.Max] = (x, y) =>
      if (math.abs(x.index - y.index) <= 1) {
        List(x, y).minBy(_.index).some
      } else {
        None
      }
    val result = ExtremumCollector.mergeCollectReverse(extremums, 2)
    val expected = List(
      Extremum.Max(1, -3),
      Extremum.Max(1.01, 1)
    )
    assertEquals(result, expected)
  }
}
