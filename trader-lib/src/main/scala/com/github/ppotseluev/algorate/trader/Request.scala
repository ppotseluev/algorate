package com.github.ppotseluev.algorate.trader

import com.github.ppotseluev.algorate.Ticker

sealed trait Request

object Request {
  case object ShowState extends Request
  case object Sell extends Request
  case object Buy extends Request
  case object Exit extends Request
  case object ShowActiveTrades extends Request
  case class GeneralInput(input: String) extends Request
}
