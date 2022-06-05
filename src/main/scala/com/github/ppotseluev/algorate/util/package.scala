package com.github.ppotseluev.algorate

import cats.effect.Sync
import cats.effect.kernel.Async

import java.util.concurrent.CompletableFuture
import scala.annotation.tailrec
import scala.collection.immutable.NumericRange

package object util {
  def fromJavaFuture[F[_]: Async, T](future: => CompletableFuture[T]): F[T] =
    Async[F].fromCompletableFuture(Sync[F].delay(future))

  def split[T, N: Integral](interval: Interval[T, N], range: N, offset: N): Seq[Interval[T, N]] = {
    implicit val bc: BiConverter[T, N] = interval.bc
    val num = implicitly[Numeric[N]]
    import num._
    @tailrec
    def loop(acc: List[Interval[T, N]], source: NumericRange[N]): List[Interval[T, N]] =
      if (source.isEmpty) {
        acc
      } else {
        val right = min(source.end, source.start + range)
        val newInterval = Interval(bc.applyB(source.start), bc.applyB(right))
        val rest = source.copy(start = right + offset, end = source.end, step = source.step)
        loop(newInterval :: acc, rest)
      }
    loop(List.empty, interval.toRange).reverse
  }

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
