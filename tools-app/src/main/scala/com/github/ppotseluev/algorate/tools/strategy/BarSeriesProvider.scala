package com.github.ppotseluev.algorate.tools.strategy

import cats.effect.Async
import cats.syntax.functor._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.trader.BarsConverter
import org.ta4j.core.BarSeries
import ru.tinkoff.piapi.contract.v1.Share

private[strategy] class BarSeriesProvider[F[_]: Async](broker: Broker[F]) {

  def getBarSeries(
      share: Share,
      interval: CandlesInterval
  ): F[BarSeries] =
    for {
      bars <- broker.getData(share.getFigi, interval)
    } yield BarsConverter.buildBarSeries(share.getTicker, bars)
}
