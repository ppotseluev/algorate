package com.github.ppotseluev.algorate.cats

import cats.ApplicativeError
import cats.ApplicativeThrow

object CatsUtils {

  implicit class EitherSyntax[A, B](val either: Either[A, B]) extends AnyVal {
    def toF[E, F[_]: ApplicativeError[*[_], E]](implicit ev: A <:< E): F[B] =
      ApplicativeError[F, E].fromEither(either.left.map(ev))

    def toF[F[_]: ApplicativeError[*[_], A]]: F[B] =
      ApplicativeError[F, A].fromEither(either)

    def toFT[F[_]: ApplicativeThrow](implicit ev: A <:< Throwable): F[B] =
      toF[Throwable, F]
  }
}
