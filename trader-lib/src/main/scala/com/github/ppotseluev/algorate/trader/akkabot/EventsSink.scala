package com.github.ppotseluev.algorate.trader.akkabot

import com.github.ppotseluev.algorate.charts.TradingCharts
import com.github.ppotseluev.algorate.math.PrettyDuration._
import com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot
import com.github.ppotseluev.algorate.trader.telegram.BotToken
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient

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
        val msg = s"""
             |instrument: ${snapshot.ticker}
             |state: ${snapshot.state}
             |stats: ${snapshot.tradingStats}
             |start time: ${snapshot.firstBarTs}
             |last data: ${snapshot.lastBar.map(_.endTime).fold("?")(_.toString)}
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
