package com.github.ppotseluev.algorate.core
import cats.Monad
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Ticker
import com.github.ppotseluev.algorate.util.Interval.Time
import io.chrisdavenport.mules.Cache
import ru.tinkoff.piapi.contract.v1.Share

class CachedBroker[F[_]: Monad](
    sharesCache: Cache[F, Unit, List[Share]],
    broker: Broker[F]
) extends Broker[F] {
  override def getShare(ticker: Ticker): F[Share] = broker.getShare(ticker)

  override def getAllShares: F[List[Share]] =
    sharesCache.lookup(()).flatMap {
      case Some(shares) => shares.pure
      case None         => broker.getAllShares.flatMap(s => sharesCache.insert((), s).as(s))
    }

  override def placeOrder(order: Order): F[OrderId] = broker.placeOrder(order)

  override def getData(instrumentId: InstrumentId, interval: Time): F[Seq[Bar]] =
    broker.getData(instrumentId, interval)
}
