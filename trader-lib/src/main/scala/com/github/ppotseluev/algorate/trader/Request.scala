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
  case object Features extends Request("features".some)
  def values: List[Request] = List(
    ShowState,
    Sell,
    Buy,
    Exit,
    ShowActiveTrades,
    GetBalance,
    Features
  )

  case class GeneralInput(input: String) extends Request(none)
}
