package com.github.ppotseluev.algorate.trader.app

import akka.actor.typed.ActorSystem
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.unsafe.implicits.global
import cats.implicits._
import cats.~>
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffBroker
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
      tickersMap: Map[Ticker, InstrumentId],
      streamFrom: LocalDate = LocalDate.now.minusDays(10),
      streamTo: LocalDate = LocalDate.now.minusDays(2),
      rate: FiniteDuration = 1.second
  )

  val assets: Map[InstrumentId, TradingAsset] = Map.empty
//    List(
//      TradingAsset("BBG000BNJHS8", "LUV", "usd"),
//      TradingAsset("BBG000BRJL00", "PPL", "usd"),
//      TradingAsset("BBG000BBJQV0", "NVDA", "usd"),
//      TradingAsset("BBG000BBS2Y0", "AMGN", "usd"),
//      TradingAsset("BBG006Q0HY77", "CFG", "usd"),
//      TradingAsset("BBG00NNG2ZJ8", "XRX", "usd"),
//      TradingAsset("BBG000BNSZP1", "MCD", "usd"),
//      TradingAsset("BBG000K4ND22", "CVX", "usd"),
//      TradingAsset("BBG000BGRY34", "CVS", "usd"),
//      TradingAsset("BBG006L8G4H1", "YNDX", "rub"),
//      TradingAsset("BBG007TJF1N7", "QRVO", "usd"),
//      TradingAsset(
//        "BBG004PYF2N3",
//        "POLY",
//        "rub"
//      ), //TODO 2 shares fount for ticker, investigate the reason
//      TradingAsset("BBG0025Y4RY4", "ABBV", "usd"),
//      TradingAsset("BBG000C17X76", "BIIB", "usd"),
//      TradingAsset("BBG000CL9VN6", "NFLX", "usd"),
//      TradingAsset("BBG001M8HHB7", "TRIP", "usd"),
//      TradingAsset("BBG000H8TVT2", "TGT", "usd"),
//      TradingAsset("BBG000PSKYX7", "V", "usd"),
//      TradingAsset("BBG000BB6KF5", "MET", "usd"),
//      TradingAsset("BBG000C3J3C9", "CSCO", "usd"),
//      TradingAsset("BBG000BKFZM4", "GLW", "usd"),
//      TradingAsset("BBG000D4LWF6", "MDLZ", "usd"),
//      TradingAsset("BBG004731354", "ROSN", "rub"),
//      TradingAsset("BBG000GZQ728", "XOM", "usd"),
//      TradingAsset("BBG000CGC1X8", "QCOM", "usd"),
//      TradingAsset("BBG000R7Z112", "DAL", "usd"),
//      TradingAsset("BBG000BQQ2S6", "OXY", "usd"),
//      TradingAsset("BBG000WCFV84", "LYB", "usd"),
//      TradingAsset("BBG000C5Z1S3", "MU", "usd"),
//      TradingAsset("BBG000BCTLF6", "BAC", "usd"),
//      TradingAsset("BBG000BJF1Z8", "FDX", "usd"),
//      TradingAsset("BBG000Q3JN03", "RF", "usd"),
//      TradingAsset("BBG000BNFLM9", "LRCX", "usd"),
//      TradingAsset("BBG000BWNFZ9", "WDC", "usd"),
//      TradingAsset("BBG000FDBX90", "CNP", "usd"),
//      TradingAsset("BBG000BS0ZF1", "RL", "usd"),
//      TradingAsset("BBG00475K6C3", "CHMF", "rub"),
//      TradingAsset("BBG002583CV8", "PINS", "usd"),
//      TradingAsset("BBG000C5HS04", "NKE", "usd"),
//      TradingAsset("BBG000G0Z878", "HIG", "usd"),
//      TradingAsset("BBG000BMQPL1", "KEY", "usd")
//    ).groupMapReduce(_.instrumentId)(identity)((_, _) => ???)

  val useHistoricalData: Option[StubSettings] = None
//    Some( //None to stream realtime market data
//      StubSettings(tickersMap)
//    )

  private def wrapBroker[F[_]](toF: IO ~> F)(broker: Broker[IO]): Broker[F] =
    new Broker[F] {
      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        toF(broker.placeOrder(order))

      override def getData(
          instrumentId: InstrumentId,
          interval: Broker.CandlesInterval
      ): F[List[Bar]] =
        toF(broker.getData(instrumentId, interval))

      override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
        toF(broker.getOrderInfo(orderId))
    }

  private def wrapEventsSink[F[_]](toF: IO ~> F)(eventsSink: EventsSink[IO]): EventsSink[F] =
    (event: Event) => toF(eventsSink.push(event))

  override def run(_a: List[String]): IO[ExitCode] = {
    logger.info("Hello from Algorate!")
    val factory = Factory.io
    val brokerResource = factory.tinkoffBroker.map(
      if (useHistoricalData.isDefined) TinkoffBroker.testBroker else identity
    )
    val eventsSinkResource = factory.telegramEventsSink
    val program = for {
      broker <- brokerResource
      eventsSink <- eventsSinkResource
    } yield {
      val eventsSinkFuture = wrapEventsSink(λ[IO ~> Future](_.unsafeToFuture()))(eventsSink)
      val brokerFuture = wrapBroker(λ[IO ~> Future](_.unsafeToFuture()))(broker)
      val figiList = assets.keys.toList
      val moneyTracker = TinkoffBroker.moneyTracker(broker)
      val policy = new MoneyManagementPolicy(() => moneyTracker.get)(
        maxPercentage = 0.025, //2.5%
        maxAbsolute = Map(
          "usd" -> 200,
          "rub" -> 15000
        ),
        allowFractionalLots = true
      )
      val tradingManager = TradingManager(
        assets = assets,
        broker = brokerFuture,
        strategy = Strategies.default,
        moneyTracker = moneyTracker,
        policy = policy,
        keepLastBars = 12 * 60,
        eventsSink = eventsSinkFuture,
        maxLag = Option.when(useHistoricalData.isEmpty)(90.seconds)
      )
      for {
        actorSystem <- IO(ActorSystem(tradingManager, "Algorate"))
        requestHandler = factory.traderRequestHandler(
          actorSystem = actorSystem,
          assets = assets.map { case (id, asset) => asset.ticker -> id },
          eventsSink = eventsSink
        )
        api = factory.traderApi(requestHandler)
        exitCode <- useHistoricalData.fold {
          {
            val subscriber = MarketSubscriber
              .fromActor(actorSystem)
              .stub[IO](
                broker,
                rate = 0.millis,
                streamFrom = LocalDate.now,
                streamTo = LocalDate.now
              )
            figiList.parTraverse(subscriber.subscribe).void
          } *> MarketSubscriber //TODO fix gap between historical and realtime data
            .fromActor(actorSystem)
            .using[IO](factory.investApi)
            .subscribe(figiList)
        } { case StubSettings(instruments, streamFrom, streamTo, rate) =>
          val subscriber = MarketSubscriber
            .fromActor(actorSystem)
            .stub[IO](
              broker,
              rate = rate,
              streamFrom = streamFrom,
              streamTo = streamTo
            )
          instruments.values.toList.parTraverse(subscriber.subscribe).void
        } &> moneyTracker.run &> api.run
      } yield exitCode
    }
    program.useEval
  }

}
