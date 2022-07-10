package com.github.ppotseluev.algorate

import cats.effect.Sync
import cats.effect.kernel.Async
import java.util.concurrent.CompletableFuture

package object util {
  def fromJavaFuture[F[_]: Async, T](future: => CompletableFuture[T]): F[T] =
    Async[F].fromCompletableFuture(Sync[F].delay(future))

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
