package com.github.ppotseluev.algorate.math

import munit.FunSuite

class RealNumberSpec extends FunSuite {

  test("BigDecimal <-> RealNumber") {
    val testCases = List(
      BigDecimal(12.15) -> RealNumber(12, 15),
      BigDecimal(12) -> RealNumber(12, 0),
      BigDecimal(0.1) -> RealNumber(0, 1)
    )
    testCases.foreach { case (bigDecimal, realNumber) =>
      assertEquals(bigDecimal.asRealNumber, realNumber)
      assertEquals(realNumber.asBigDecimal, bigDecimal)
    }
  }
}
