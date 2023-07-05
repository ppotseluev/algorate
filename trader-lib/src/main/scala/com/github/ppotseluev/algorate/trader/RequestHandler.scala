package com.github.ppotseluev.algorate.trader

import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.MessageSource

trait RequestHandler[F[_]] {
  def handle(request: Request, reply: MessageSource => F[Unit]): F[Unit]
}
