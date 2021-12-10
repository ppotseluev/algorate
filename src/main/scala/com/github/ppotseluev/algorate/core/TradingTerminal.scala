package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.{Order, OrderId, Price}

trait TradingTerminal[F[_]] {

  def placeOrder(
      order: Order,
      takeProfit: Option[Price],
      stopLoss: Option[Price]
  ): F[OrderId]
}
