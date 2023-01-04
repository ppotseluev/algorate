package com.github.ppotseluev.algorate.trader

import cats.effect.kernel.Async
import cats.implicits._
import org.http4s.HttpRoutes
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

object HealthCheck {

  val healthCheckEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.in("healthcheck").get.out(stringBody)

  def routes[F[_]: Async]: HttpRoutes[F] =
    Http4sServerInterpreter[F]().toRoutes {
      healthCheckEndpoint.serverLogicPure[F](_ => "OK".asRight)
    }
}
