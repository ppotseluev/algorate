package com.github.ppotseluev.algorate.trader

import com.github.ppotseluev.algorate.trader.policy.Policy.TradeRequest

package object policy {
  type Policy = TradeRequest => Policy.Decision
}
