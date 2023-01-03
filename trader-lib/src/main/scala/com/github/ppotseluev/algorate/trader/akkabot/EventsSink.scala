package com.github.ppotseluev.algorate.trader.akkabot

import com.github.ppotseluev.algorate.trader.TelegramClient
import com.github.ppotseluev.algorate.trader.TelegramClient.BotToken
import com.github.ppotseluev.algorate.trader.akkabot.Event.TradingSnapshot

trait EventsSink[F[_]] {
  def push(event: Event): F[_]
}

object EventsSink {
  def telegram[F[_]](
      botToken: BotToken,
      chatId: String,
      client: TelegramClient[F]
  ): EventsSink[F] = { case TradingSnapshot(snapshot, aggregatedStats) =>
    val text =
      s"""
        |--------------------
        |
        |instrument: ${snapshot.instrumentId}
        |state: ${snapshot.state}
        |stats: ${snapshot.tradingStats}
        |start time: ${snapshot.firstBarTs}
        |last data: ${snapshot.lastBar.map(_.endTime).fold("?")(_.toString)}
        |lag: ${snapshot.lag}
        |aggregated stats: $aggregatedStats
        |
        |--------------------
        |""".stripMargin
//    val image = TradingCharts.buildImage(
//      strategyBuilder = snapshot.strategyBuilder,
//      series = snapshot.unsafe.barSeries,
//      tradingStats = snapshot.tradingStats.some,
//      title = snapshot.instrumentId
//    )
//    val outputFile = new File("image.png")
//    if (!outputFile.exists()) {
//      outputFile.createNewFile()
//    }
//    val writer = new FileOutputStream(outputFile)
//    writer.write(image)
    val messageSource = TelegramClient.MessageSource(
      chatId = chatId,
      text = text,
      photo = None,
      replyMarkup = None,
      parseMode = None
    )
    client.send(botToken)(messageSource)
  }
}
