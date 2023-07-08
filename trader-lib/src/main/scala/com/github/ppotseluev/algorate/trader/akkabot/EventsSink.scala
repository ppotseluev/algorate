package com.github.ppotseluev.algorate.trader.akkabot

import cats.ApplicativeThrow
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.math.PrettyDuration._
import com.github.ppotseluev.algorate.trader.LoggingSupport
import com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot
import com.github.ppotseluev.algorate.trader.telegram.BotToken
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient
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
            val profit = new ProfitLossPercentageCriterion() //todo take from stats
            val longProfit =
              profit.calculate(snapshot.unsafe.barSeries, snapshot.unsafe.longHistory)
            val shortProfit =
              profit.calculate(snapshot.unsafe.barSeries, snapshot.unsafe.shortHistory)
            val msg = s"""
             |asset: ${snapshot.asset}
             |price: ${snapshot.currentPrice}
             |state: ${snapshot.state}
             |stats: ${snapshot.tradingStats}
             |long profit: $longProfit%, short profit: $shortProfit%
             |lag: ${snapshot.lag.map(_.pretty)}
             |aggregated stats: $aggregatedStats
             |triggeredBy: ${snapshot.triggeredBy}
             |balance: $money
             |""".stripMargin
            val img = TradingCharts.buildImage(
              strategyBuilder = snapshot.strategyBuilder,
              assetData = snapshot.unsafeAssetData,
              tradingStats = Some(snapshot.tradingStats),
              title = s"${snapshot.asset.ticker}"
            )
            (msg, img)
        }
        TelegramClient.Message(
          chatId = chatId,
          text = text,
          photo = image.some
        )
      }
      .flatMap { messageSource => client.send(botToken)(messageSource) }
      .onError { case e: Throwable => Sync[F].delay(logger.error("Can't push event", e)) }
}
