package com.github.ppotseluev.algorate.cats

import cats.ApplicativeError
import cats.ApplicativeThrow
import cats.effect.IO
import cats.effect.unsafe.IORuntime

object CatsUtils {
  trait FireAndForget[F[_]] {
    private[FireAndForget] def apply(f: F[_]): Unit
  }
  object FireAndForget {
    def apply[F[_]: FireAndForget]: FireAndForget[F] = implicitly[FireAndForget[F]]
    implicit def io(implicit runtime: IORuntime): FireAndForget[IO] = f => f.unsafeRunAndForget()

    implicit class Syntax[F[_]](val f: F[_]) extends AnyVal {
      def fireAndForget()(implicit F: FireAndForget[F]): Unit = F.apply(f)
    }
  }

  implicit class EitherSyntax[A, B](val either: Either[A, B]) extends AnyVal {
    def toF[E, F[_]: ApplicativeError[*[_], E]](implicit ev: A <:< E): F[B] =
      ApplicativeError[F, E].fromEither(either.left.map(ev))

    def toF[F[_]: ApplicativeError[*[_], A]]: F[B] =
      ApplicativeError[F, A].fromEither(either)

    def toFT[F[_]: ApplicativeThrow](implicit ev: A <:< Throwable): F[B] =
      toF[Throwable, F]
  }
}
