package com.github.ppotseluev.algorate.math

case class RealNumber(
    integerPart: Long,
    decimalPart: Int
) {
  override def toString: String =
    s"$integerPart.$decimalPart"
}
