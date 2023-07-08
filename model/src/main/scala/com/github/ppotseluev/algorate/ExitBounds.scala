package com.github.ppotseluev.algorate

case class ExitBounds(
    takeProfit: Price,
    stopLoss: Price
) {
  def shouldExit(price: Price): Boolean = {
    val min = takeProfit.min(stopLoss)
    val max = takeProfit.max(stopLoss)
    price <= min || price >= max
  }
}
