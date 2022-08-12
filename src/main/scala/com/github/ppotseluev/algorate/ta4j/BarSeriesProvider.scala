package com.github.ppotseluev.algorate.ta4j

import cats.effect.Async
import cats.syntax.functor._
import com.github.ppotseluev.algorate.core.Broker
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share

class BarSeriesProvider[F[_]: Async](broker: Broker[F]) {

  def getBarSeries(
      share: Share,
      interval: CandlesInterval
  ): F[BarSeries] =
    for {
      bars <- broker.getData(share.getFigi, interval)
    } yield Utils.buildBarSeries(share.getTicker, bars)
}
