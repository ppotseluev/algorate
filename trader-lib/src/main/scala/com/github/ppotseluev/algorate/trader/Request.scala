package com.github.ppotseluev.algorate.trader

import cats.implicits._

sealed abstract class Request(val command: Option[String])

object Request {
  def fromString(string: String): Request =
    values.find(_.command.contains(string)).getOrElse(GeneralInput(string))

  case object ShowState extends Request("show".some)
  case object Sell extends Request("sell".some)
  case object Buy extends Request("buy".some)
  case object Exit extends Request("exit".some)
  case object ShowActiveTrades extends Request(none) //TODO
  case object GetBalance extends Request("balance".some)
  case object GetOpenOrders extends Request("open_orders".some)
  case object GetAllOrders extends Request("all_orders".some)
  case object Features extends Request("features".some)
  def values: List[Request] = List( //TODO enum
    ShowState,
    Sell,
    Buy,
    Exit,
    ShowActiveTrades,
    GetBalance,
    Features,
    GetOpenOrders,
    GetAllOrders
  )

  case class GeneralInput(input: String) extends Request(none)
}
