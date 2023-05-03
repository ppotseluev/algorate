package com.github.ppotseluev.algorate.broker.tinkoff

import cats.Functor
import cats.Parallel
import cats.effect.Sync
import cats.effect.Temporal
import cats.effect.kernel.Async
import cats.implicits._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.Order.Type
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Archive
import com.github.ppotseluev.algorate.broker.ArchiveCachedBroker
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.Day
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.broker.LoggingBroker
import com.github.ppotseluev.algorate.broker.MoneyTracker
import com.github.ppotseluev.algorate.broker.RedisCachedBroker
import com.github.ppotseluev.algorate.broker.TestBroker
import com.github.ppotseluev.algorate.cats.Provider
import com.github.ppotseluev.algorate.math._
import com.typesafe.scalalogging.LazyLogging
import dev.profunktor.redis4cats.RedisCommands
import java.nio.file.Path
import java.time.ZoneId
import ru.tinkoff.piapi.contract.v1.CandleInterval
import ru.tinkoff.piapi.contract.v1.HistoricCandle
import ru.tinkoff.piapi.contract.v1.OrderDirection
import ru.tinkoff.piapi.contract.v1.OrderType
import ru.tinkoff.piapi.contract.v1.Quotation
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.models.Positions
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

object TinkoffBroker {
  trait Ops[F[_]] {
    def getAllShares: F[List[Share]]

    final def getShareById(id: InstrumentId)(implicit F: Functor[F]): F[Share] =
      getAllShares
        .map(_.filter(_.getFigi == id))
        .map { relatedShares =>
          require(relatedShares.size == 1, s"${relatedShares.size} shares found for figi $id")
          relatedShares.head
        }

    final def getSharesById(ids: Set[InstrumentId])(implicit F: Functor[F]): F[List[Share]] =
      getAllShares
        .map(_.filter(s => ids.contains(s.getFigi)))
        .map {
          _.groupBy(_.getFigi).map { case (id, sharesWithSameId) =>
            require(
              sharesWithSameId.size == 1,
              s"${sharesWithSameId.size} shares found for figi $id"
            )
            sharesWithSameId.head
          }.toList
        }

    final def getShareByTicker(ticker: Ticker)(implicit F: Functor[F]): F[Share] =
      getAllShares
        .map(_.filter(_.getTicker == ticker))
        .map { relatedShares =>
          require(relatedShares.size == 1, s"${relatedShares.size} shares found for ticker $ticker")
          relatedShares.head
        }

    def getPositions: F[Positions]
  }

  def apply[F[_]: Sync: Parallel](
      api: TinkoffApi[F],
      brokerAccountId: BrokerAccountId,
      zoneId: ZoneId
  ): TinkoffBroker[F] = new Broker[F] with Ops[F] with LazyLogging {
    override def getAllShares: F[List[Share]] =
      api.getAllShares

    override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
      api
        .getOderState(brokerAccountId, orderId)
        .flatMap { orderState =>
          Sync[F]
            .delay {
              logger.info(
                s"Order $orderId, commission: ${orderState.getInitialCommission}, ${orderState.getServiceCommission}, ${orderState.getExecutedCommission}"
              )
            }
            .as(orderState)
        }
        .map(_.getExecutionReportStatus)
        .map(TinkoffConverters.convert)
        .map(OrderPlacementInfo(orderId, _))

    override def placeOrder(order: Order): F[OrderPlacementInfo] = {
      val lots = order.lots.toInt
      Sync[F].raiseWhen(lots != order.lots)(
        new RuntimeException(s"invalid lots ${order.lots}")
      ) >> api
        .postOrder(
          order.instrumentId,
          lots,
          price(order),
          orderDirection(order),
          brokerAccountId,
          orderType(order),
          order.key
        )
        .map { r =>
          OrderPlacementInfo(
            orderId = r.getOrderId,
            status = TinkoffConverters.convert(r.getExecutionReportStatus)
          )
        }
    }

    private def price(order: Order): Quotation = {
      val RealNumber(units, nano) = order.price.asRealNumber
      Quotation.newBuilder
        .setUnits(units)
        .setNano(nano)
        .build
    }

    private def orderDirection(order: Order): OrderDirection =
      order.operationType match {
        case OperationType.Buy  => OrderDirection.ORDER_DIRECTION_BUY
        case OperationType.Sell => OrderDirection.ORDER_DIRECTION_SELL
      }

    private def orderType(order: Order): OrderType =
      order.details.`type` match {
        case Type.Limit  => OrderType.ORDER_TYPE_LIMIT
        case Type.Market => OrderType.ORDER_TYPE_MARKET
      }

    private def convert(candleDuration: FiniteDuration)(candle: HistoricCandle): Bar =
      Bar(
        openPrice = TinkoffConverters.price(candle.getOpen),
        closePrice = TinkoffConverters.price(candle.getClose),
        lowPrice = TinkoffConverters.price(candle.getLow),
        highPrice = TinkoffConverters.price(candle.getHigh),
        volume = candle.getVolume.toDouble,
        trades = 0,
        endTime = TinkoffConverters.fromProto(candle.getTime, zoneId),
        duration = candleDuration
      )

    private def candleInterval(timeResolution: CandleResolution): CandleInterval =
      timeResolution match {
        case CandleResolution.OneMinute => CandleInterval.CANDLE_INTERVAL_1_MIN
        case CandleResolution.FiveMinute => CandleInterval.CANDLE_INTERVAL_5_MIN
      }

    override def getData(
        asset: TradingAsset,
        candlesInterval: CandlesInterval
    ): F[List[Bar]] = {
      val resolution = candlesInterval.resolution

      def get(day: Day) = {
        api
          .getCandles(
            asset.instrumentId,
            day.start,
            day.end,
            candleInterval(resolution)
          )
          .map(
            _.map(convert(resolution.duration))
          )
      }

      candlesInterval.interval.days.parTraverse(get).map(_.flatten)
    }

    override def getPositions: F[Positions] = api.getPositions(brokerAccountId)
  }

  def testBroker[F[_]: Async: Parallel](broker: TinkoffBroker[F]): TinkoffBroker[F] = {
    new Broker[F] with Ops[F] {
      private val testBroker = TestBroker.wrap(broker)

      override def getAllShares: F[List[Share]] =
        broker.getAllShares

      override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
        testBroker.getOrderInfo(orderId)

      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        testBroker.placeOrder(order)

      override def getData(
          asset: TradingAsset,
          candlesInterval: CandlesInterval
      ): F[List[Bar]] =
        testBroker.getData(asset, candlesInterval)

      override def getPositions: F[Positions] = broker.getPositions
    }
  }

  def withLogging[F[_]: Sync](_broker: TinkoffBroker[F]): TinkoffBroker[F] =
    new Broker[F] with Ops[F] {
      private val broker = new LoggingBroker(_broker)

      override def getAllShares: F[List[Share]] =
        _broker.getAllShares

      override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
        broker.getOrderInfo(orderId)

      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        broker.placeOrder(order)

      override def getData(
          asset: TradingAsset,
          candlesInterval: CandlesInterval
      ): F[List[Bar]] =
        broker.getData(asset, candlesInterval)

      override def getPositions: F[Positions] = _broker.getPositions
    }

  def withCaching[F[_]: Sync: Parallel](
      token: String,
      _broker: TinkoffBroker[F],
      barsCache: Either[Path, RedisCommands[F, String, List[Bar]]],
      sharesCache: RedisCommands[F, String, List[Share]],
      sharesTtl: FiniteDuration = 1.day
  ): TinkoffBroker[F] =
    new Broker[F] with Ops[F] {
      private val sharesKey = "shares"
      private val broker = barsCache match {
        case Left(archiveDir) =>
          val archive = new Archive[F](token, archiveDir)
          new ArchiveCachedBroker(_broker, archive)
        case Right(redisCache) =>
          new RedisCachedBroker(_broker, redisCache)
      }
      override def getAllShares: F[List[Share]] =
        sharesCache.get(sharesKey).flatMap {
          case Some(shares) => shares.pure[F]
          case None =>
            _broker.getAllShares.flatMap { s =>
              sharesCache.setEx(sharesKey, s, sharesTtl).as(s)
            }
        }

      override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
        broker.getOrderInfo(orderId)

      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        broker.placeOrder(order)

      override def getData(
          asset: TradingAsset,
          candlesInterval: CandlesInterval
      ): F[List[Bar]] =
        broker.getData(asset, candlesInterval)

      override def getPositions: F[Positions] = _broker.getPositions
    }

  def moneyTracker[F[_]: Sync: Temporal](broker: TinkoffBroker[F]): MoneyTracker[F] = {
    val getMoney = Sync[F].map(broker.getPositions) { positions =>
      positions.getMoney.asScala.groupMapReduce(_.getCurrency)(x => BigDecimal(x.getValue))(_ + _)
    }
    new Provider(getMoney)
  }
}
