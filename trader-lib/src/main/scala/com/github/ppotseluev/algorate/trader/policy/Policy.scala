package com.github.ppotseluev.algorate.trader.policy

import cats.Semigroup
import cats.data.NonEmptyList
import cats.implicits._
import com.github.ppotseluev.algorate.Price
import com.github.ppotseluev.algorate.TradingAsset

object Policy {
  def combine(policy: Policy, policies: Policy*): Policy = request =>
    NonEmptyList(policy, policies.toList).reduceMap(_.apply(request))

  case class TradeRequest(
      asset: TradingAsset,
      price: Price,
      manualTrade: Boolean
  )

  sealed trait Decision {
    def allowedOrElse(decision: Decision): Decision = this match {
      case allowed: Decision.Allowed => allowed
      case _: Decision.Denied        => decision
    }
    def lots: Double
  }

  object Decision {
    implicit val semigroup: Semigroup[Decision] = (d1, d2) =>
      (d1, d2) match {
        case (Allowed(lots1), Allowed(lots2))     => Allowed(lots1 min lots2)
        case (Allowed(_), Denied(_))              => d2
        case (Denied(_), Allowed(_))              => d1
        case (Denied(message1), Denied(message2)) => Denied(s"$message1, $message2")
      }

    case class Allowed(lots: Double) extends Decision
    case class Denied(message: String) extends Decision {
      override def lots: Double = 0
    }
  }
}
