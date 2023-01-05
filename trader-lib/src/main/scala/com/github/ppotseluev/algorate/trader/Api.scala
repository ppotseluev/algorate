package com.github.ppotseluev.algorate.trader

import cats.Parallel
import cats.effect.Async
import cats.effect.ExitCode
import cats.implicits._
import com.github.ppotseluev.algorate.trader.telegram.TelegramWebhook
import com.github.ppotseluev.algorate.trader.telegram.WebhookSecret
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

class Api[F[_]: Async: Parallel](
    telegramHandler: TelegramWebhook.Handler[F],
    telegramWebhookSecret: WebhookSecret,
    prometheusMetrics: PrometheusMetrics[F],
    config: Api.Config
) {

  private val serverOptions = Http4sServerOptions.customiseInterceptors
    .metricsInterceptor(prometheusMetrics.metricsInterceptor())
    .options

  private def buildRoutes(endpoints: ServerEndpoint[Any, F]*): HttpRoutes[F] =
    Http4sServerInterpreter(serverOptions).toRoutes(endpoints.toList)

  private val routes: HttpRoutes[F] = buildRoutes(
    TelegramWebhook.webhookEndpoint(telegramHandler, telegramWebhookSecret)
  )

  private val operationalRoutes: HttpRoutes[F] = buildRoutes(
    HealthCheck.healthCheckEndpoint,
    prometheusMetrics.metricsEndpoint
  )

  private def run(port: Int, routes: HttpRoutes[F]): F[ExitCode] =
    BlazeServerBuilder
      .apply[F]
      .bindHttp(port = port, host = "0.0.0.0")
      .withHttpApp(routes.orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Error)

  val runOperationalServer: F[ExitCode] = run(config.operationalPort, operationalRoutes)
  val runServer: F[ExitCode] = run(config.port, routes)
  val run: F[ExitCode] = runServer &> runOperationalServer
}

object Api {
  case class Config(port: Int, operationalPort: Int)
}
