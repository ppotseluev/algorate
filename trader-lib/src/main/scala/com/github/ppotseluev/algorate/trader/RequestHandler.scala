package com.github.ppotseluev.algorate.trader

trait RequestHandler[F[_]] {
  def handle(request: Request): F[Unit]
}
