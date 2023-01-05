package com.github.ppotseluev.algorate.trader

import cats.Parallel
import cats.effect.Async
import cats.effect.ExitCode
import cats.implicits._
import com.github.ppotseluev.algorate.trader.telegram.TelegramWebhook
import com.github.ppotseluev.algorate.trader.telegram.WebhookSecret
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.blaze.server.BlazeServerBuilder
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

class Api[F[_]: Async: Parallel](
    telegramHandler: TelegramWebhook.Handler[F],
    telegramWebhookSecret: WebhookSecret,
    prometheusMetrics: PrometheusMetrics[F],
    config: Api.Config
) {
  private val metricRoutes: HttpRoutes[F] = {
    val serverOptions = Http4sServerOptions.customiseInterceptors
      .metricsInterceptor(prometheusMetrics.metricsInterceptor())
      .options
    Http4sServerInterpreter(serverOptions).toRoutes(prometheusMetrics.metricsEndpoint)
  }

  private val operationalApp: HttpApp[F] =
    List(
      metricRoutes,
      HealthCheck.routes
    ).reduce(_ <+> _).orNotFound

  private val httpApp: HttpApp[F] =
    List(
      TelegramWebhook.routes(telegramHandler, telegramWebhookSecret)
    ).reduce(_ <+> _).orNotFound

  private def run(port: Int, app: HttpApp[F]): F[ExitCode] =
    BlazeServerBuilder
      .apply[F]
      .bindHttp(port = port, host = "0.0.0.0")
      .withHttpApp(app)
      .serve
      .compile
      .drain
      .as(ExitCode.Error)

  val runOperationalServer: F[ExitCode] = run(config.operationalPort, operationalApp)
  val runServer: F[ExitCode] = run(config.port, httpApp)
  val run: F[ExitCode] = runServer &> runOperationalServer
}

object Api {
  case class Config(port: Int, operationalPort: Int)
}
