package com.github.ppotseluev.algorate.trader

import com.github.ppotseluev.algorate.Ticker

sealed trait Request

object Request {
  case class ShowState(ticker: Ticker) extends Request
}
