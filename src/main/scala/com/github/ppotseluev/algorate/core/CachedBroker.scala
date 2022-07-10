package com.github.ppotseluev.algorate.core
import cats.Monad
import cats.implicits._
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Ticker
import dev.profunktor.redis4cats.RedisCommands
import io.chrisdavenport.mules.Cache
import ru.tinkoff.piapi.contract.v1.Share

class CachedBroker[F[_]: Monad](
    sharesCache: Cache[F, Unit, List[Share]],
    broker: Broker[F],
    barsCache: RedisCommands[F, String, Bar]
) extends Broker[F] {
  override def getShare(ticker: Ticker): F[Share] = broker.getShare(ticker)

  override def getAllShares: F[List[Share]] =
    sharesCache.lookup(()).flatMap {
      case Some(shares) => shares.pure[F]
      case None         => broker.getAllShares.flatMap(s => sharesCache.insert((), s).as(s))
    }

  override def placeOrder(order: Order): F[OrderId] = broker.placeOrder(order)

  override def getData(instrumentId: InstrumentId, candlesInterval: CandlesInterval): F[Seq[Bar]] = {
//    def key(n: Long): String = s"${timeInterval.resolution}_$n"
//    val keys = timeInterval.interval.toRange.map(key).toSet
//    for {
//      cached <- barsCache.mGet(keys)
//    } yield ???
      broker.getData(instrumentId, candlesInterval)
//    broker.getData(instrumentId, timeInterval)
  }
}
