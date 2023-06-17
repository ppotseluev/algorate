package com.github.ppotseluev.algorate.trader.app

import akka.actor.typed.ActorSystem
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.unsafe.implicits.global
import cats.implicits._
import cats.~>
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.{Broker, TestBroker}
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.cats.Provider
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.trader.akkabot.Event
import com.github.ppotseluev.algorate.trader.akkabot.EventsSink
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager
import com.github.ppotseluev.algorate.trader.policy.MoneyManagementPolicy
import com.typesafe.scalalogging.LazyLogging

import java.time.LocalDate
import scala.concurrent.Future
import scala.concurrent.duration._

object AkkaTradingApp extends IOApp with LazyLogging {

  case class StubSettings(
      assets: List[TradingAsset],
      streamFrom: LocalDate = LocalDate.now.minusDays(10),
      streamTo: LocalDate = LocalDate.now.minusDays(2),
      rate: FiniteDuration = 1.second
  )

  val candleResolution: CandleResolution = CandleResolution.FiveMinute

  val useHistoricalData: Option[StubSettings] = None
//    Some( //None to stream realtime market data
//      StubSettings(tickersMap)
//    )

  private def wrapBroker[F[_]](toF: IO ~> F)(broker: Broker[IO]): Broker[F] =
    new Broker[F] {
      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        toF(broker.placeOrder(order))

      override def getData(
          asset: TradingAsset,
          interval: Broker.CandlesInterval
      ): F[List[Bar]] =
        toF(broker.getData(asset, interval))

      override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
        toF(broker.getOrderInfo(orderId))
    }

  private def wrapEventsSink[F[_]](toF: IO ~> F)(eventsSink: EventsSink[IO]): EventsSink[F] =
    (event: Event) => toF(eventsSink.push(event))

  override def run(_a: List[String]): IO[ExitCode] = {
    logger.info("Hello from Algorate!")
    val factory = Factory.io
    val brokerResource = factory.binanceBroker
    val eventsSinkResource = factory.telegramEventsSink
    val program = for {
      broker <- brokerResource
      eventsSink <- eventsSinkResource
      binanceApi <- factory.binanceApi
    } yield {
      val eventsSinkFuture = wrapEventsSink(λ[IO ~> Future](_.unsafeToFuture()))(eventsSink)
      val brokerFuture = wrapBroker(λ[IO ~> Future](_.unsafeToFuture()))(broker)
      val moneyTracker = new Provider[IO, Money](
        IO.never[Money],
        Map("usdt" -> BigDecimal(100_000)).some
      )
      val policy = new MoneyManagementPolicy(() => moneyTracker.get)(
        maxPercentage = 1, //100%
        maxAbsolute = Map(
          "usd" -> 1_000,
          "usdt" -> 1_000,
          "rub" -> 80_000
        )
      )
      val assets = Assets.allCryptocurrencies
      val assetsMap = assets.map(a => a.instrumentId -> a).toMap
      val tradingManager = TradingManager(
        assets = assetsMap,
        broker = brokerFuture,
        strategy = Strategies.default,
        moneyTracker = moneyTracker,
        policy = policy,
        keepLastBars = 2000,
        eventsSink = eventsSinkFuture,
        maxLag = Option.when(useHistoricalData.isEmpty)(
          (candleResolution.duration * 1.5).asInstanceOf[FiniteDuration]
        )
      )
      for {
//        shares <- broker.getSharesById(Assets.sharesIds.toSet)
//        shares.map { s =>
//          TradingAsset(
//            instrumentId = s.getFigi,
//            ticker = s.getTicker,
//            currency = s.getCurrency,
//            `type` = TradingAsset.Type.Share,
//            sector = s.getSector
//          )
//        }
        actorSystem <- IO(ActorSystem(tradingManager, "Algorate"))
        requestHandler = factory.traderRequestHandler(
          actorSystem = actorSystem,
          assets = assetsMap.map { case (id, asset) => asset.ticker -> id },
          eventsSink = eventsSink
        )
        api = factory.traderApi(requestHandler)
        subscription = MarketSubscriber.fromActor(actorSystem, candleResolution)
        exitCode <- useHistoricalData.fold {
          {
            val subscriber = subscription.stub[IO](
              broker,
              rate = 0.millis,
              streamFrom = LocalDate.now.minusDays(6),
              streamTo = LocalDate.now
            )
            assets.parTraverse(subscriber.subscribe).void
          } *>
            subscription //TODO fix gap between historical and realtime data
              .binance[IO](binanceApi)
              .subscribe(assets)
        } { case StubSettings(assets, streamFrom, streamTo, rate) =>
          val subscriber = subscription.stub[IO](
            broker,
            rate = rate,
            streamFrom = streamFrom,
            streamTo = streamTo
          )
          assets.parTraverse(subscriber.subscribe).void
        } &> moneyTracker.run &> api.run
      } yield exitCode
    }
    program.useEval
  }

}
