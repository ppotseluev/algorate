package com.github.ppotseluev.algorate.trader.policy

import cats.implicits._
import cats.{Align, Semigroup}
import cats.data.{Ior, NonEmptyList}
import com.github.ppotseluev.algorate.{Currency, Price}
import com.github.ppotseluev.algorate.trader.policy.Policy.{Decision, TradeRequest}

trait Policy {
  def apply(request: TradeRequest): Decision
}

object Policy {
  def combine(policy: Policy, policies: Policy*): Policy = request =>
    NonEmptyList(policy, policies.toList).reduceMap(_.apply(request))

  case class TradeRequest(
      price: Price,
      currency: Currency
  )

  sealed trait Decision
  object Decision {
    implicit val semigroup: Semigroup[Decision] = (d1, d2) =>
      (d1, d2) match {
        case (Allowed(lots1), Allowed(lots2))     => Allowed(lots1 min lots2)
        case (Allowed(_), Denied(_))              => d2
        case (Denied(_), Allowed(_))              => d1
        case (Denied(message1), Denied(message2)) => Denied(s"$message1, $message2")
      }

    case class Allowed(lots: Int) extends Decision
    case class Denied(message: String) extends Decision
  }
}
