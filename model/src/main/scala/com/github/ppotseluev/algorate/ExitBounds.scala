package com.github.ppotseluev.algorate

case class ExitBounds(
    takeProfit: Price,
    stopLoss: Price
) {
  def max: Price = takeProfit.max(stopLoss)
  def min: Price = takeProfit.min(stopLoss)
  def shouldExit(price: Price): Boolean = price <= min || price >= max
}
