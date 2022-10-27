package com.github.ppotseluev.algorate

import akka.actor.typed.ActorRef

package object akkabot {
  type Trader = ActorRef[Trader.Event]
}
