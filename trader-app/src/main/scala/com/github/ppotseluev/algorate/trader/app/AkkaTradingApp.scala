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
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
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

  val assets: Map[InstrumentId, TradingAsset] =
    Map(
      "BBG000BNJHS8" -> TradingAsset("LUV", "usd"),
      "BBG000BRJL00" -> TradingAsset("PPL", "usd"),
      "BBG000BBJQV0" -> TradingAsset("NVDA", "usd"),
      "BBG000BBS2Y0" -> TradingAsset("AMGN", "usd"),
      "BBG006Q0HY77" -> TradingAsset("CFG", "usd"),
      "BBG00NNG2ZJ8" -> TradingAsset("XRX", "usd"),
      "BBG000BNSZP1" -> TradingAsset("MCD", "usd"),
      "BBG000K4ND22" -> TradingAsset("CVX", "usd"),
      "BBG000BGRY34" -> TradingAsset("CVS", "usd"),
      "BBG006L8G4H1" -> TradingAsset("YNDX", "rub"),
      "BBG007TJF1N7" -> TradingAsset("QRVO", "usd"),
      "BBG004PYF2N3" -> TradingAsset("POLY", "rub"),
      "BBG0025Y4RY4" -> TradingAsset("ABBV", "usd"),
      "BBG000C17X76" -> TradingAsset("BIIB", "usd"),
      "BBG000CL9VN6" -> TradingAsset("NFLX", "usd"),
      "BBG001M8HHB7" -> TradingAsset("TRIP", "usd"),
      "BBG000H8TVT2" -> TradingAsset("TGT", "usd"),
      "BBG000PSKYX7" -> TradingAsset("V", "usd"),
      "BBG000BB6KF5" -> TradingAsset("MET", "usd"),
      "BBG000C3J3C9" -> TradingAsset("CSCO", "usd"),
      "BBG000BKFZM4" -> TradingAsset("GLW", "usd"),
      "BBG000D4LWF6" -> TradingAsset("MDLZ", "usd"),
      "BBG004731354" -> TradingAsset("ROSN", "rub"),
      "BBG000GZQ728" -> TradingAsset("XOM", "usd"),
      "BBG000CGC1X8" -> TradingAsset("QCOM", "usd"),
      "BBG000R7Z112" -> TradingAsset("DAL", "usd"),
      "BBG000BQQ2S6" -> TradingAsset("OXY", "usd"),
      "BBG000WCFV84" -> TradingAsset("LYB", "usd"),
      "BBG000C5Z1S3" -> TradingAsset("MU", "usd"),
      "BBG000BCTLF6" -> TradingAsset("BAC", "usd"),
      "BBG000BJF1Z8" -> TradingAsset("FDX", "usd"),
      "BBG000Q3JN03" -> TradingAsset("RF", "usd"),
      "BBG000BNFLM9" -> TradingAsset("LRCX", "usd"),
      "BBG000BWNFZ9" -> TradingAsset("WDC", "usd"),
      "BBG000FDBX90" -> TradingAsset("CNP", "usd"),
      "BBG000BS0ZF1" -> TradingAsset("RL", "usd"),
      "BBG00475K6C3" -> TradingAsset("CHMF", "rub"),
      "BBG002583CV8" -> TradingAsset("PINS", "usd"),
      "BBG000C5HS04" -> TradingAsset("NKE", "usd"),
      "BBG000G0Z878" -> TradingAsset("HIG", "usd"),
      "BBG000BMQPL1" -> TradingAsset("KEY", "usd")
    )

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
      val policy = new MoneyManagementPolicy(moneyTracker)(
        maxPercentage = 0.025,
        maxAbsolute = Map(
          "usd" -> 200,
          "rub" -> 15000
        )
      ).andThen(_.allowedOrElse(Decision.Allowed(1))) //TODO remove this temporary experiment
      val tradingManager = TradingManager(
        assets = assets,
        broker = brokerFuture,
        strategy = Strategies.intraChannel,
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
