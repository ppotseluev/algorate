package com.github.ppotseluev.algorate.trader.akkabot

import cats.ApplicativeThrow
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.math.PrettyDuration._
import com.github.ppotseluev.algorate.trader.LoggingSupport
import com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot
import com.github.ppotseluev.algorate.trader.telegram.{BotToken, TelegramClient}
import org.ta4j.core.analysis.criteria.pnl.ProfitLossPercentageCriterion

trait EventsSink[F[_]] {
  def push(event: Event): F[Unit]
}

object EventsSink extends LoggingSupport {
  private val logger = getLogger("EventsSink")

  def telegram[F[_]: Sync](
      botToken: BotToken,
      chatId: String,
      client: TelegramClient[F]
  ): EventsSink[F] = event =>
    ApplicativeThrow[F]
      .catchNonFatal {
        val (text, image) = event match {
          case TradingSnapshot(snapshot, aggregatedStats, money) =>
            val profit = new ProfitLossPercentageCriterion()
            val longProfit =
              profit.calculate(snapshot.unsafe.barSeries, snapshot.unsafe.longHistory)
            val shortProfit =
              profit.calculate(snapshot.unsafe.barSeries, snapshot.unsafe.shortHistory)
            val msg = s"""
             |asset: ${snapshot.asset}
             |state: ${snapshot.state}
             |stats: ${snapshot.tradingStats}
             |long profit: $longProfit%, short profit: $shortProfit%
             |lag: ${snapshot.lag.map(_.pretty)}
             |aggregated stats: $aggregatedStats
             |triggeredBy: ${snapshot.triggeredBy}
             |balance: ${money.view.mapValues(_.setScale(1)).toMap}
             |""".stripMargin
            val img = TradingCharts.buildImage(
              strategyBuilder = snapshot.strategyBuilder,
              series = snapshot.unsafe.barSeries,
              tradingStats = Some(snapshot.tradingStats),
              title = s"${snapshot.asset.ticker}"
            )
            (msg, img)
        }
        TelegramClient.MessageSource(
          chatId = chatId,
          text = text,
          photo = image,
          replyMarkup = None,
          parseMode = None
        )
      }
      .flatMap { messageSource => client.send(botToken)(messageSource) }
      .onError { case e: Throwable => Sync[F].delay(logger.error("Can't push event", e)) }
}
