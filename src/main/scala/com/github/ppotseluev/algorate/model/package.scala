package com.github.ppotseluev.algorate

import com.softwaremill.tagging._

package object model {
  type InstrumentId = String @@ Tags.InstrumentId
  type BrokerAccountId = String @@ Tags.BrokerAccountId
  type OrderId = String @@ Tags.OrderId
  type Price = Double @@ Tags.Price
  type Ticker = String @@ Tags.Ticker
}
