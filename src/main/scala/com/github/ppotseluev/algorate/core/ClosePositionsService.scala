package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.ClosePositionOrder

trait ClosePositionsService[F[_]] {
  def place(order: ClosePositionOrder): F[Unit]
}
