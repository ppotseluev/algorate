package com.github.ppotseluev.algorate.server

import akka.actor.typed.ActorSystem
import cats.Parallel
import cats.effect.{IO, Resource}
import cats.effect.kernel.Async
import com.github.ppotseluev.algorate.{InstrumentId, Ticker}
import com.github.ppotseluev.algorate.broker.tinkoff.{TinkoffApi, TinkoffBroker}
import com.github.ppotseluev.algorate.trader.{Api, RequestHandler}
import com.github.ppotseluev.algorate.trader.akkabot.{EventsSink, RequestHandlerImpl, TradingManager}
import com.github.ppotseluev.algorate.trader.telegram.{HttpTelegramClient, TelegramClient, TelegramWebhook}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.tinkoff.piapi.core.InvestApi
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import upperbound.Limiter

import java.time.ZoneOffset

class Factory[F[_]: Async: Parallel] {

  val config: Config = ConfigSource.default
    .load[Config]
    .fold(
      error => throw new RuntimeException(error.prettyPrint()),
      identity
    )
  import config._

  lazy val prometheusMetrics: PrometheusMetrics[F] = PrometheusMetrics.default[F]()

  lazy val investApi: InvestApi = InvestApi.createSandbox(tinkoffAccessToken)

  val redisClient: Resource[F, RedisClient] = RedisClient[F].from("redis://localhost")

  val tinkoffBroker: Resource[F, TinkoffBroker[F]] = {
    for {
      candlesLimiter <- Limiter.start[F](candlesMinInterval)
//      redisClient <- redisClient TODO
//      barsCache <- Redis[F].fromClient(
//        redisClient,
//        RedisCodecs.byteBuffer.stringKeys.boopickleValues[List[Bar]]
//      )
//      sharesCache <- Redis[F].fromClient(
//        redisClient,
//        RedisCodecs[String, String].jsonValues[List[Share]]
//      )
      broker = {
        val tinkoffApi = TinkoffApi
          .wrap[F](investApi)
          .withCandlesLimit(candlesLimiter)
          .withLogging
        TinkoffBroker.withLogging(
//          TinkoffBroker.withCaching(
          TinkoffBroker[F](tinkoffApi, accountId, ZoneOffset.UTC)
//            barsCache,
//            sharesCache
//          )
        )
      }
    } yield broker

  }

  val telegramClient: Resource[F, TelegramClient[F]] =
    HttpClientFs2Backend.resource().map { sttpBackend =>
      new HttpTelegramClient[F](telegramUrl, sttpBackend)
    }

  val telegramEventsSink: Resource[F, EventsSink[F]] =
    telegramClient.map(EventsSink.telegram(telegramBotToken, telegramChatId, _))

  def traderRequestHandler(
      actorSystem: ActorSystem[TradingManager.Event],
      shares: Map[Ticker, InstrumentId],
      eventsSink: EventsSink[F]
  ): RequestHandler[F] =
    new RequestHandlerImpl[F](actorSystem, shares, eventsSink)

  def telegramWebhookHandler(
      requestHandler: RequestHandler[F]
  ): TelegramWebhook.Handler[F] =
    new TelegramWebhook.Handler[F](
      allowedUsers = telegramUsersWhitelist,
      trackedChats = telegramTrackedChats,
      requestHandler = requestHandler
    )

  def traderApi(requestHandler: RequestHandler[F]): Api[F] = new Api(
    telegramHandler = telegramWebhookHandler(requestHandler),
    telegramWebhookSecret = telegramWebhookSecret,
    prometheusMetrics = prometheusMetrics,
    config = apiConfig
  )
}

object Factory {
  val io = new Factory[IO]
}
