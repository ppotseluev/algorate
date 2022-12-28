package com.github.ppotseluev.algorate.broker

package object tinkoff {
  type TinkoffBroker[F[_]] = Broker[F] with TinkoffBroker.Ops[F]
}
