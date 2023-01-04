package com.github.ppotseluev.algorate.trader

import cats.effect.Async
import cats.effect.ExitCode
import cats.implicits._
import com.github.ppotseluev.algorate.trader.telegram.TelegramWebhook
import com.github.ppotseluev.algorate.trader.telegram.WebhookSecret
import org.http4s.HttpApp
import org.http4s.blaze.server.BlazeServerBuilder

class Api[F[_]: Async](
    handler: TelegramWebhook.Handler[F],
    telegramWebhookSecret: WebhookSecret
) {
  val httpApp: HttpApp[F] =
    List(
      TelegramWebhook.routes(handler, telegramWebhookSecret),
      HealthCheck.routes
    ).reduce(_ <+> _).orNotFound

  val runServer: F[ExitCode] =
    BlazeServerBuilder
      .apply[F]
      .bindHttp(host = "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Error)
}
