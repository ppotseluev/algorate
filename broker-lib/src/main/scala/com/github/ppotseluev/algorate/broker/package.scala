package com.github.ppotseluev.algorate

import com.github.ppotseluev.algorate.cats.Provider

package object broker {
  type MoneyTracker[F[_]] = Provider[F, Map[Currency, BigDecimal]]
}
