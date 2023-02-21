package com.github.ppotseluev.algorate.trader.akkabot

import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.math.PrettyDuration._
import com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot
import com.github.ppotseluev.algorate.trader.telegram.BotToken
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient
import org.ta4j.core.analysis.criteria.pnl.{GrossProfitCriterion, ProfitLossCriterion, ProfitLossPercentageCriterion}

trait EventsSink[F[_]] {
  def push(event: Event): F[Unit]
}

object EventsSink {
  def telegram[F[_]](
      botToken: BotToken,
      chatId: String,
      client: TelegramClient[F]
  ): EventsSink[F] = event => {
    val (text, image) = event match {
      case TradingSnapshot(snapshot, aggregatedStats) =>
        val profit = new ProfitLossPercentageCriterion()
        val longProfit = profit.calculate(snapshot.unsafe.barSeries, snapshot.unsafe.longHistory)
        val shortProfit = profit.calculate(snapshot.unsafe.barSeries, snapshot.unsafe.shortHistory)
        val msg = s"""
             |instrument: ${snapshot.ticker}
             |state: ${snapshot.state}
             |stats: ${snapshot.tradingStats}
             |long profit: $longProfit%, short profit: $shortProfit%
             |lag: ${snapshot.lag.map(_.pretty)}
             |aggregated stats: $aggregatedStats
             |triggeredBy: ${snapshot.triggeredBy}
             |""".stripMargin
        val img = TradingCharts.buildImage(
          strategyBuilder = snapshot.strategyBuilder,
          series = snapshot.unsafe.barSeries,
          tradingStats = Some(snapshot.tradingStats),
          title = s"${snapshot.ticker}"
        )
        (msg, img)
    }
    val messageSource = TelegramClient.MessageSource(
      chatId = chatId,
      text = text,
      photo = image,
      replyMarkup = None,
      parseMode = None
    )
    client.send(botToken)(messageSource)
  }
}
