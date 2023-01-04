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
import com.typesafe.scalalogging.LazyLogging
import java.time.LocalDate
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.Future
import scala.concurrent.duration._

object AkkaTradingApp extends IOApp with LazyLogging {

  case class StubSettings(
      ticker: Ticker,
      streamFrom: LocalDate = LocalDate.now.minusDays(10),
      streamTo: LocalDate = LocalDate.now.minusDays(2),
      rate: FiniteDuration = 1.millis
  )

  val useHistoricalData: Option[StubSettings] = None
//    Some( //None to stream realtime market data
//    StubSettings("CSCO")
//  )

  val tickersMap: Map[Ticker, InstrumentId] = Map(
    "LUV" -> "BBG000BNJHS8",
    "FDX" -> "BBG000BJF1Z8",
    "DAL" -> "BBG000R7Z112",
    "LYB" -> "BBG000WCFV84",
    "CHMF" -> "BBG00475K6C3",
    "POLY" -> "BBG004PYF2N3",
    "QRVO" -> "BBG007TJF1N7",
    "MU" -> "BBG000C5Z1S3",
    "LRCX" -> "BBG000BNFLM9",
    "NVDA" -> "BBG000BBJQV0",
    "XRX" -> "BBG00NNG2ZJ8",
    "CSCO" -> "BBG000C3J3C9",
    "PINS" -> "BBG002583CV8",
    "WDC" -> "BBG000BWNFZ9",
    "QCOM" -> "BBG000CGC1X8",
    "GLW" -> "BBG000BKFZM4",
    "XOM" -> "BBG000GZQ728",
    "ROSN" -> "BBG004731354",
    "CVX" -> "BBG000K4ND22",
    "OXY" -> "BBG000BQQ2S6",
    "PPL" -> "BBG000BRJL00",
    "CNP" -> "BBG000FDBX90",
    "NKE" -> "BBG000C5HS04",
    "TGT" -> "BBG000H8TVT2",
    "MDLZ" -> "BBG000D4LWF6",
    "MCD" -> "BBG000BNSZP1",
    "RL" -> "BBG000BS0ZF1",
    "NFLX" -> "BBG000CL9VN6",
    "YNDX" -> "BBG006L8G4H1",
    "TRIP" -> "BBG001M8HHB7",
    "MET" -> "BBG000BB6KF5",
    "KEY" -> "BBG000BMQPL1",
    "V" -> "BBG000PSKYX7",
    "CFG" -> "BBG006Q0HY77",
    "BAC" -> "BBG000BCTLF6",
    "HIG" -> "BBG000G0Z878",
    "RF" -> "BBG000Q3JN03",
    "BIIB" -> "BBG000C17X76",
    "AMGN" -> "BBG000BBS2Y0",
    "CVS" -> "BBG000BGRY34",
    "ABBV" -> "BBG0025Y4RY4"
    //    "PHOR",
    //    "LKOH",
  )
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

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("Hello from Algorate!")
    val token = args.head
    val accountId = args(1)
    val telegramBotToken = args(2)
    val telegramChatId = args(3)
    val investApi = InvestApi.createSandbox(token)
    val brokerResource = Factory
      .tinkoffBroker[IO](
        accountId = accountId,
        investApi = investApi
      )
      .map(if (useHistoricalData.isDefined) TinkoffBroker.testBroker else identity)
    val eventsSinkResource = Factory
      .telegramEventsSink[IO](
        botToken = telegramBotToken,
        chatId = telegramChatId
      )
      .map(wrapEventsSink(λ[IO ~> Future](_.unsafeToFuture())))
    val program = for {
      broker <- brokerResource
      eventsSink <- eventsSinkResource
    } yield {
      val brokerFuture = wrapBroker(λ[IO ~> Future](_.unsafeToFuture()))(broker)
      val figiList = tickersMap.values.toList
      val tradingManager = TradingManager(
        tradingInstruments = figiList.toSet,
        broker = brokerFuture,
        strategy = Strategies.intraChannel,
        keepLastBars = 1000,
        eventsSink = eventsSink,
        maxLag = Option.when(useHistoricalData.isEmpty)(90.seconds)
      )
      for {
        actorSystem <- IO(ActorSystem(tradingManager, "Algorate"))
        _ <- useHistoricalData.fold {
          {
            val subscriber = MarketSubscriber
              .fromActor(actorSystem)
              .stub[IO](
                broker,
                rate = 0.millis,
                streamFrom = LocalDate.now,
                streamTo = LocalDate.now
              )
            figiList.traverse(subscriber.subscribe).void
          } *> MarketSubscriber
            .fromActor(actorSystem)
            .using[IO](investApi)
            .subscribe(figiList)
        } { case StubSettings(ticker, streamFrom, streamTo, rate) =>
          MarketSubscriber
            .fromActor(actorSystem)
            .stub[IO](
              broker,
              rate = rate,
              streamFrom = streamFrom,
              streamTo = streamTo
            )
            .subscribe(tickersMap(ticker))
        } &> IO.never
        // &> CommandHandler.handleUserCommand[IO](actorSystem, tickersMap).foreverM FIXME it causes EOFException in Docker
      } yield ExitCode.Error
    }
    program.useEval
  }

}
