package com.github.ppotseluev.algorate.server

import akka.actor.typed.ActorSystem
import boopickle.Default.iterablePickler
import cats.Parallel
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.Console
import cats.implicits._
import com.binance.api.client.BinanceApiClientFactory
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.InstrumentId
import com.github.ppotseluev.algorate.Ticker
import com.github.ppotseluev.algorate.broker.Archive
import com.github.ppotseluev.algorate.broker.tinkoff.BinanceBroker
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.cats.CatsUtils.FireAndForget
import com.github.ppotseluev.algorate.redis.RedisCodecs
import com.github.ppotseluev.algorate.redis.codec._
import com.github.ppotseluev.algorate.server.Codecs._
import com.github.ppotseluev.algorate.trader.Api
import com.github.ppotseluev.algorate.trader.RequestHandler
import com.github.ppotseluev.algorate.trader.akkabot.EventsSink
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager
import com.github.ppotseluev.algorate.trader.cli.AlgorateCli
import com.github.ppotseluev.algorate.trader.feature.FeatureToggles
import com.github.ppotseluev.algorate.trader.telegram.HttpTelegramClient
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient
import com.github.ppotseluev.algorate.trader.telegram.TelegramWebhook
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import io.github.paoloboni.binance.BinanceClient
import io.github.paoloboni.binance.spot.SpotApi

import java.io.File
import java.time.ZoneOffset
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import upperbound.Limiter

class Factory[F[_]: Async: Parallel: FireAndForget] {

  val config: Config = ConfigSource
    .resources("application.local.conf")
    .optional
    .withFallback(ConfigSource.resources("application.conf"))
    .load[Config]
    .fold(
      error => throw new RuntimeException(error.prettyPrint()),
      identity
    )
  import config._

  lazy implicit val featureToggles: FeatureToggles = FeatureToggles.inMemory

  lazy val prometheusMetrics: PrometheusMetrics[F] = PrometheusMetrics.default[F]()

  lazy val investApi: InvestApi = InvestApi.createSandbox(tinkoffAccessToken)

  lazy val binanceApi: Resource[F, SpotApi[F]] = BinanceClient.createSpotClient(binanceConfig)

  lazy val binanceClientFactory = BinanceApiClientFactory
    .newInstance(
      binanceConfig.apiKey,
      binanceConfig.apiSecret,
      binanceConfig.testnet,
      binanceConfig.testnet
    )

  lazy val binanceSpotClient = binanceClientFactory.newAsyncRestClient()

  lazy val binanceMarginClient = binanceClientFactory.newAsyncMarginRestClient()

  lazy val binanceBroker: Resource[F, BinanceBroker[F]] =
    for {
      api <- binanceApi
      barsCache <-
        if (enableBrokerCache)
          redisClient
            .flatMap { redis =>
              Redis[F]
                .fromClient(
                  redis,
                  RedisCodecs.byteBuffer.stringKeys.boopickleValues[List[Bar]]
                )
            }
            .map(_.some)
        else Resource.pure[F, Option[RedisCommands[F, String, List[Bar]]]](none)
    } yield barsCache match {
      case Some(cache) =>
        BinanceBroker.cached(
          api,
          binanceSpotClient,
          binanceMarginClient,
          Right(cache)
        )
      case None =>
        new BinanceBroker(
          api,
          binanceSpotClient,
          binanceMarginClient
        )
    }

  val redisClient: Resource[F, RedisClient] = RedisClient[F].from("redis://localhost")

  val archive = new Archive[F](
    tinkoffAccessToken,
    new File(historicalDataArchive.getOrElse("unknown")).toPath
  )

  val tinkoffBroker: Resource[F, TinkoffBroker[F]] = for {
    candlesLimiter <- Limiter.start[F](candlesMinInterval)
    broker = {
      val tinkoffApi = TinkoffApi
        .wrap[F](investApi)
        .withCandlesLimit(candlesLimiter)
        .withLogging
//      TinkoffBroker.withLogging(
      TinkoffBroker[F](tinkoffApi, accountId, ZoneOffset.UTC)
//      )
    }
    resultBroker <-
      if (enableBrokerCache) {
        for {
          redisClient <- redisClient
          barsCache <- Redis[F].fromClient(
            redisClient,
            RedisCodecs.byteBuffer.stringKeys.boopickleValues[List[Bar]]
          )
          sharesCache <- Redis[F].fromClient(
            redisClient,
            RedisCodecs[String, String].jsonValues[List[Share]]
          )
          cache = historicalDataArchive match {
            case Some(path) => new File(path).toPath.asLeft
            case None       => barsCache.asRight
          }
        } yield TinkoffBroker.withCaching[F](tinkoffAccessToken, broker, cache, sharesCache)
      } else {
        Resource.pure[F, TinkoffBroker[F]](broker)
      }
  } yield resultBroker

  val telegramClient: Resource[F, TelegramClient[F]] =
    HttpClientFs2Backend.resource().map { sttpBackend =>
      new HttpTelegramClient[F](telegramUrl, sttpBackend)
    }

  def telegramEventsSink(telegramClient: TelegramClient[F]): EventsSink[F] =
    EventsSink.telegram(telegramBotToken, telegramChatId, telegramClient)

  def traderRequestHandler(
      actorSystem: ActorSystem[TradingManager.Event],
      assets: Map[Ticker, InstrumentId],
      broker: BinanceBroker[F]
  ): F[RequestHandler[F]] =
    Ref.of[F, State](State.Empty).map { state =>
      new RequestHandlerImpl[F](
        actorSystem = actorSystem,
        assets = assets,
        state = state,
        broker = broker
      )
    }

  def telegramWebhookHandler(
      requestHandler: RequestHandler[F],
      telegramClient: TelegramClient[F]
  ): TelegramWebhook.Handler[F] =
    new TelegramWebhook.Handler[F](
      allowedUsers = telegramUsersWhitelist,
      trackedChats = telegramTrackedChats,
      botToken = telegramBotToken,
      telegramClient = telegramClient,
      requestHandler = requestHandler
    )

  def traderApi(
      requestHandler: RequestHandler[F],
      telegramClient: TelegramClient[F]
  ): Api[F] = new Api(
    telegramHandler = telegramWebhookHandler(requestHandler, telegramClient),
    telegramWebhookSecret = telegramWebhookSecret,
    prometheusMetrics = prometheusMetrics,
    config = apiConfig
  )

  def algorateCli(
      requestHandler: RequestHandler[F],
      telegramClient: TelegramClient[F]
  )(implicit console: Console[F]) = new AlgorateCli[F](
    requestHandler = requestHandler,
    telegramClient = telegramClient,
    chatId = telegramChatId,
    botToken = telegramBotToken
  )
}

object Factory {
  implicit val io = {
    import cats.effect.unsafe.implicits.global
    new Factory[IO]
  }
}
