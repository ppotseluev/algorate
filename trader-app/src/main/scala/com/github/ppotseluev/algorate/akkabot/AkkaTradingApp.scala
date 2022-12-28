package com.github.ppotseluev.algorate.akkabot

import akka.actor.typed.ActorSystem
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.unsafe.implicits.global
import cats.implicits._
import cats.~>
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.broker.TestBroker
import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.InstrumentId
import com.github.ppotseluev.algorate.model.Order
import com.github.ppotseluev.algorate.model.OrderId
import com.github.ppotseluev.algorate.model.Ticker
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.ta4j.Strategies
import com.typesafe.scalalogging.LazyLogging
import java.time.LocalDate
import ru.tinkoff.piapi.contract.v1.OrderState
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.core.InvestApi
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object AkkaTradingApp extends IOApp with LazyLogging {

  case class StubSettings(
      ticker: Ticker,
      streamFrom: LocalDate = LocalDate.now.minusDays(5),
      streamTo: LocalDate = LocalDate.now.minusDays(2),
      rate: FiniteDuration = 10.millis
  )

  val useHistoricalData: Option[StubSettings] = None
//    Some( //None to stream realtime market data
//    StubSettings("CSCO")
//  )

  val _tickers = List(
    "LUV",
    "FDX",
    "DAL",
    "LYB",
    "CHMF",
    "PHOR",
    "POLY",
    "QRVO",
    "MU",
    "LRCX",
    "NVDA",
    "XRX",
    "CSCO",
    "PINS",
    "WDC",
    "QCOM",
    "GLW",
    "LKOH",
    "XOM",
    "ROSN",
    "CVX",
    "OXY",
    "PPL",
    "CNP",
    "NKE",
    "TGT",
    "MDLZ",
    "MCD",
    "RL",
    "NFLX",
    "YNDX",
    "TRIP",
    "MET",
    "KEY",
    "V",
    "CFG",
    "BAC",
    "HIG",
    "RF",
    "BIIB",
    "AMGN",
    "CVS",
    "ABBV"
  )

  val tickers = _tickers

  private def wrapBroker[F[_]](toF: IO ~> F)(broker: Broker[IO]): Broker[F] =
    new Broker[F] {
      override def getAllShares: F[List[Share]] =
        toF(broker.getAllShares)

      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        toF(broker.placeOrder(order))

      override def getData(
          instrumentId: InstrumentId,
          interval: Broker.CandlesInterval
      ): F[List[Bar]] =
        toF(broker.getData(instrumentId, interval))

      override def getOrderState(orderId: OrderId): F[OrderState] =
        toF(broker.getOrderState(orderId))
    }

  override def run(args: List[String]): IO[ExitCode] = {
    val token = args.head
    val accountId = args(1)
    val investApi = InvestApi.createSandbox(token)
    Factory
      .tinkoffBroker[IO](
        accountId = accountId,
        investApi = investApi
      )
      .map(if (useHistoricalData.isDefined) TestBroker.wrap else identity)
      .use { broker =>
        val brokerFuture = wrapBroker(λ[IO ~> Future](_.unsafeToFuture()))(broker)
        for {
          shares <- tickers.traverse(broker.getShare)
          figiList = shares.map(_.getFigi)
          tradingManager = TradingManager(
            tradingInstruments = figiList.toSet,
            broker = brokerFuture,
            strategy = Strategies.intraChannel,
            keepLastBars = 100000
          )
          actorSystem <- IO(ActorSystem(tradingManager, "Algorate"))
          _ <- useHistoricalData.fold {
            MarketSubscriber
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
              .subscribe(shares.find(_.getTicker == ticker).get.getFigi)
          } &> CommandHandler.handleUserCommand[IO](actorSystem, shares).foreverM
        } yield ExitCode.Error
      }
  }

}
