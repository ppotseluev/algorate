package com.github.ppotseluev.algorate

package object math {

  implicit class BigDecimalOps(val number: BigDecimal) extends AnyVal {
    def asRealNumber: RealNumber = {
      val integerPart = number.bigDecimal.toBigInteger.longValueExact
      val decimal = number - integerPart
      val decimalPart = decimal.bigDecimal.movePointRight(decimal.scale).intValueExact
      RealNumber(
        integerPart = integerPart,
        decimalPart = decimalPart
      )
    }
  }

  implicit class RealNumberOps(val number: RealNumber) extends AnyVal {
    def asBigDecimal: BigDecimal =
      BigDecimal(number.toString)
  }
}
