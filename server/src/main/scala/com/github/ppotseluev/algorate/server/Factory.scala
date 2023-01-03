package com.github.ppotseluev.algorate.server

import cats.Parallel
import cats.effect.Resource
import cats.effect.kernel.Async
import com.github.ppotseluev.algorate.BrokerAccountId
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffApi
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.trader.HttpTelegramClient
import com.github.ppotseluev.algorate.trader.TelegramClient
import com.github.ppotseluev.algorate.trader.TelegramClient.BotToken
import com.github.ppotseluev.algorate.trader.akkabot.EventsSink
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import java.time.ZoneOffset
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.duration._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import upperbound.Limiter
import upperbound.syntax.rate._

object Factory {

  def redisClient[F[_]: Async]: Resource[F, RedisClient] =
    RedisClient[F].from("redis://localhost")

  def tinkoffBroker[F[_]: Parallel: Async](
      accountId: BrokerAccountId,
      investApi: InvestApi,
      candlesMinInterval: FiniteDuration = 300 every 1.minute
  ): Resource[F, TinkoffBroker[F]] = {
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

  def telegramClient[F[_]: Async]: Resource[F, TelegramClient[F]] =
    HttpClientFs2Backend.resource().map { sttpBackend =>
      new HttpTelegramClient[F]("https://api.telegram.org", sttpBackend)
    }

  def telegramEventsSink[F[_]: Async](
      botToken: BotToken,
      chatId: String
  ): Resource[F, EventsSink[F]] =
    telegramClient.map(EventsSink.telegram(botToken, chatId, _))
}
