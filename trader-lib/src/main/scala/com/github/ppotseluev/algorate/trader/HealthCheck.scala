package com.github.ppotseluev.algorate.trader

import cats.effect.kernel.Async
import cats.implicits._
import sttp.tapir._

object HealthCheck {

  val healthCheckEndpointDef: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("healthcheck").get.out(stringBody)

  def healthCheckEndpoint[F[_]: Async] =
    healthCheckEndpointDef.serverLogicPure[F](_ => "OK".asRight)
}
