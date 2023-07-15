package com.github.ppotseluev.algorate.trader.akkabot

import akka.actor.typed.ActorSystem
import cats.effect.Ref
import cats.effect.kernel.Sync
import cats.implicits._
import com.github.ppotseluev.algorate.ClosePositionOrder.StopType
import com.github.ppotseluev.algorate.ClosePositionOrder.StopType.findValues
import com.github.ppotseluev.algorate.ExitBounds
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.tinkoff.BinanceBroker
import com.github.ppotseluev.algorate.cats.CatsUtils.FireAndForget
import com.github.ppotseluev.algorate.strategy.FullStrategy.TradeIdea
import com.github.ppotseluev.algorate.trader.Request
import com.github.ppotseluev.algorate.trader.RequestHandler
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.{AssetsFilter, State}
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State.{
  WaitingAssetsFilter,
  WaitingCancelOrdersTicker,
  WaitingExitTicker,
  WaitingFeatureAction,
  WaitingFeatureName,
  WaitingFeatureValue,
  WaitingOrdersTicker,
  WaitingShowTicker,
  WaitingStopLoss,
  WaitingStopTicker,
  WaitingStopType,
  WaitingStopValue,
  WaitingTakeProfit,
  WaitingTradingTicker
}
import com.github.ppotseluev.algorate.trader.feature.FeatureToggles
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.KeyboardButton
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.MessageSource
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.ReplyMarkup
import com.typesafe.scalalogging.LazyLogging
import enumeratum.{Enum, EnumEntry}
import com.github.ppotseluev.algorate.cats.CatsUtils.FireAndForget._

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class RequestHandlerImpl[F[_]: Sync: FireAndForget](
    actorSystem: ActorSystem[TradingManager.Event],
    assets: Map[Ticker, InstrumentId],
    state: Ref[F, State],
    broker: BinanceBroker[F]
)(implicit featureToggles: FeatureToggles)
    extends RequestHandler[F]
    with LazyLogging {

  private implicit class StateOps[T](f: F[T]) {
    def -->(newState: State): F[Unit] = f >> state.set(newState)
  }

  private def notifyTraders(
      ticker: Ticker,
      event: InstrumentId => TradingManager.Event,
      flushState: Boolean = true
  ) =
    Sync[F].delay {
      assets.get(ticker).foreach { instrumentId =>
        actorSystem ! event(instrumentId)
      }
    } >> state.set(State.Empty).whenA(flushState)

  override def handle(request: Request, reply: MessageSource => F[Unit]): F[Unit] = Sync[F]
    .defer {
      def replyT(txt: String, removeKeyboard: Option[Boolean] = true.some) = reply(
        MessageSource(
          txt,
          replyMarkup = ReplyMarkup(removeKeyboard = removeKeyboard).some
        )
      )

      val hideKeyboard = reply(
        MessageSource(
          "Enter command",
          replyMarkup = ReplyMarkup(removeKeyboard = true.some).some
        )
      )

      def requestTicker(newState: State) =
        replyT("Enter ticker") --> newState

      request match {
        case Request.GetBalance =>
          broker
            .getBalance(nonZero = true)
            .map(_.toString)
            .flatMap(replyT(_))
        case Request.MarginBalance =>
          broker.getMarginAccount
            .map(_.getUserAssets.asScala.filter(_.getNetAsset != "0"))
            .map(_.toString)
            .flatMap(replyT(_))
        case Request.Debt =>
          broker.getMarginAccount
            .map {
              _.getUserAssets.asScala.filter { asset =>
                asset.getBorrowed != "0" || asset.getInterest != "0"
              }
            }
            .map(_.toString)
            .flatMap(replyT(_))
        case Request.CancelOrders  => requestTicker(WaitingCancelOrdersTicker)
        case Request.GetOpenOrders => requestTicker(WaitingOrdersTicker(onlyOpen = true))
        case Request.GetAllOrders  => requestTicker(WaitingOrdersTicker(onlyOpen = false))
        case Request.ShowState     => requestTicker(WaitingShowTicker)
        case Request.Sell          => requestTicker(WaitingTradingTicker(OperationType.Sell))
        case Request.Buy           => requestTicker(WaitingTradingTicker(OperationType.Buy))
        case Request.Exit          => requestTicker(WaitingExitTicker)
        case Request.SetStop =>
          val msg = MessageSource(
            text = s"Select stop type",
            replyMarkup = ReplyMarkup.make(
              StopType.values.map(_.entryName).map(KeyboardButton.apply)
            ).some
          )
          reply(msg) --> WaitingStopType
        case Request.Find =>
          val msg = MessageSource(
            text = s"Select filter",
            replyMarkup = ReplyMarkup.make(
              AssetsFilter.values.map(_.entryName).map(KeyboardButton.apply)
            ).some
          )
          reply(msg) --> WaitingAssetsFilter
        case Request.GeneralInput(input) =>
          val ticker =
            s"${input.toUpperCase.stripSuffix("USDT")}USDT" //TODO can be non-crypto asset
          val unexpectedInputReply = replyT(s"Unexpected input `$input`")
          def parse[T](f: String => T): F[T] =
            Sync[F].catchNonFatal(f(input)).onError { case _ => unexpectedInputReply }
          state.get
            .flatMap {
              case State.Empty => unexpectedInputReply
              case WaitingCancelOrdersTicker =>
                broker.cancelAllOrders(ticker) >> replyT("Done") --> State.Empty
              case WaitingTradingTicker(operation) =>
                replyT("Enter stop-loss") --> WaitingStopLoss(operation, ticker)
              case WaitingStopLoss(op, ticker) =>
                parse(_.toDouble).flatMap { stopLoss =>
                  replyT("Enter take-profit") --> WaitingTakeProfit(op, ticker, stopLoss)
                }
              case WaitingTakeProfit(operationType, ticker, stopLoss) =>
                parse(_.toDouble).flatMap { takeProfit =>
                  val trade = TradeIdea(
                    operationType,
                    ExitBounds(
                      stopLoss = stopLoss,
                      takeProfit = takeProfit
                    )
                  )
                  notifyTraders(ticker, TradingManager.Event.TradeRequested(_, trade))
                }
              case WaitingShowTicker =>
                notifyTraders(
                  ticker,
                  TradingManager.Event.TraderSnapshotRequested,
                  flushState = false
                )
              case WaitingExitTicker =>
                notifyTraders(ticker, TradingManager.Event.ExitRequested)
              case WaitingOrdersTicker(onlyOpen) =>
                broker
                  .getOrders(ticker, onlyOpen)
                  .map { orders =>
                    if (orders.isEmpty) "No orders"
                    else orders.mkString("\n\n").replaceAll(",", "\n")
                  }
                  .flatMap(replyT(_)) --> State.Empty
              case WaitingFeatureName =>
                val name = input
                featureToggles.find(name) match {
                  case Some(feature) =>
                    val msg = MessageSource(
                      text = s"Current value: ${feature.apply()}",
                      replyMarkup = ReplyMarkup.make(
                        Seq(KeyboardButton("Ok"), KeyboardButton("Update"))
                      ).some
                    )
                    reply(msg) --> WaitingFeatureAction(name)
                  case None =>
                    replyT("No such feature", removeKeyboard = none)
                }
              case WaitingFeatureAction(featureName) =>
                input match {
                  case "Ok"     => hideKeyboard --> State.Empty
                  case "Update" => replyT("Enter new value") --> WaitingFeatureValue(featureName)
                  case _        => unexpectedInputReply
                }
              case WaitingFeatureValue(featureName) =>
                val feature = featureToggles.find(featureName).get
                feature.set(input) match {
                  case Left(error) =>
                    replyT(s"Can't update: $error")
                  case Right(()) =>
                    replyT("Updated successfully") --> State.Empty
                }
              case WaitingStopTicker(stopType) =>
                replyT("Enter value") --> State.WaitingStopValue(stopType, ticker)
              case WaitingStopValue(stopType, ticker) =>
                parse(_.toDouble).flatMap { value =>
                  notifyTraders(ticker, TradingManager.Event.SetStop(_, stopType, value))
                }
              case WaitingStopType =>
                StopType.withNameOption(input) match {
                  case Some(stopType) =>
                    requestTicker(WaitingStopTicker(stopType))
                  case None =>
                    replyT("Incorrect stop type", removeKeyboard = none)
                }
              case WaitingAssetsFilter =>
                def onResult(assets: List[TradingAsset]): Unit = {
                  val msg = MessageSource(
                    text = s"Found ${assets.size} assets",
                    replyMarkup = ReplyMarkup.make(
                      assets
                        .map(_.symbol)
                        .map(KeyboardButton.apply)
                    ).some.filter(_ => assets.nonEmpty)
                  )
                  reply(msg) --> WaitingShowTicker
                }.fireAndForget()
                AssetsFilter.withNameOption(input) match {
                  case Some(assetsFilter) =>
                    replyT("Searching...") >> Sync[F].delay {
                      actorSystem ! TradingManager.Event.FindAssets(assetsFilter, onResult)
                    } --> State.Empty
                  case None =>
                    replyT("Incorrect assets filter", removeKeyboard = none)
                }
            }
        case Request.ShowActiveTrades => ???
        case Request.Features =>
          val features = featureToggles.list
          val buttons = features.map(_.name).map(KeyboardButton.apply)
          val msg = MessageSource(
            text = "Select feature",
            replyMarkup = ReplyMarkup.make(buttons).some
          )
          reply(msg) --> State.WaitingFeatureName
      }
    }
    .recover { case NonFatal(t) =>
      logger.error("Can't handle request", t)
    }
}

object RequestHandlerImpl {
  sealed trait AssetsFilter extends EnumEntry
  object AssetsFilter extends Enum[AssetsFilter] {
    sealed trait TraderFilter extends AssetsFilter
    case object All extends AssetsFilter
    case object HasChannel extends TraderFilter
    override val values: IndexedSeq[AssetsFilter] = findValues
  }

  sealed trait State
  object State {
    case object Empty extends State
    case class WaitingTradingTicker(operation: OperationType) extends State
    case object WaitingExitTicker extends State
    case object WaitingShowTicker extends State
    case object WaitingCancelOrdersTicker extends State
    case class WaitingOrdersTicker(onlyOpen: Boolean) extends State
    case object WaitingFeatureName extends State
    case class WaitingFeatureAction(featureName: String) extends State
    case class WaitingFeatureValue(featureName: String) extends State
    case class WaitingStopLoss(operationType: OperationType, ticker: Ticker) extends State
    case class WaitingTakeProfit(operationType: OperationType, ticker: Ticker, stopLoss: Price)
        extends State
    case object WaitingStopType extends State
    case class WaitingStopTicker(stopType: StopType) extends State
    case class WaitingStopValue(stopType: StopType, ticker: Ticker) extends State
    case object WaitingAssetsFilter extends State
  }
}
