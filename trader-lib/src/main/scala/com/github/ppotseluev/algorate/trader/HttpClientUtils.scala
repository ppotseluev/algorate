package com.github.ppotseluev.algorate.trader

import cats.MonadError
import cats.implicits._
import sttp.client3.Response
import sttp.model.StatusCode

object HttpClientUtils {

  implicit class RichResponse[F[_], T](val responseF: F[Response[T]]) extends AnyVal {

    def checkStatusCode(
        isSuccess: StatusCode => Boolean = _.isSuccess
    )(implicit F: MonadError[F, Throwable], ev: T <:< Either[String, _]): F[Response[T]] =
      responseF.flatMap { response =>
        if (isSuccess(response.code))
          response.pure[F]
        else
          HttpCodeException(response.code.code, response.body.left.getOrElse(""))
            .raiseError[F, Response[T]]
      }
  }

}
